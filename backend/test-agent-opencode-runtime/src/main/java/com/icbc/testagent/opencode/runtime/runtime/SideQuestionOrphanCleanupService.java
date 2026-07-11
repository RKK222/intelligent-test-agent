package com.icbc.testagent.opencode.runtime.runtime;

import com.icbc.testagent.agent.runtime.AgentRuntimeCommand;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.session.ConversationSourceType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 回收后端重启后遗留的旁路临时远端会话，并复用旁路终态事务 CAS 收敛平台 Run。
 */
@Service
public class SideQuestionOrphanCleanupService {

    public static final String ORPHAN_MESSAGE = "旁路问答超时，临时上下文已清理";

    static final Duration ORPHAN_TIMEOUT = Duration.ofMinutes(10);
    static final int SCAN_LIMIT = 200;

    private static final Logger LOGGER = LoggerFactory.getLogger(SideQuestionOrphanCleanupService.class);

    private final RunRepository runRepository;
    private final AgentRuntimeTargetResolver targetResolver;
    private final SideQuestionTerminalService terminalService;
    private final Clock clock;

    /** 注入已有 Run 端口、持久化映射目标解析器与唯一旁路终态服务。 */
    public SideQuestionOrphanCleanupService(
            RunRepository runRepository,
            AgentRuntimeTargetResolver targetResolver,
            SideQuestionTerminalService terminalService,
            Clock clock) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.targetResolver = Objects.requireNonNull(targetResolver, "targetResolver must not be null");
        this.terminalService = Objects.requireNonNull(terminalService, "terminalService must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 扫描十分钟前仍 active 的 SIDE_QUESTION Run；远端 DELETE 成功后才尝试终态 CAS，
     * DELETE 或映射恢复失败时保留 active 状态，让下一轮继续重试。
     */
    public Result cleanup(String traceId, BooleanSupplier stopRequested) {
        Objects.requireNonNull(traceId, "traceId must not be null");
        BooleanSupplier shouldStop = stopRequested == null ? () -> false : stopRequested;
        Instant updatedBefore = clock.instant().minus(ORPHAN_TIMEOUT);
        Counter counter = new Counter();
        for (Run candidate : runRepository.findStaleActiveSideQuestionRuns(updatedBefore, SCAN_LIMIT)) {
            if (shouldStop.getAsBoolean()) {
                counter.stopped = true;
                break;
            }
            counter.scannedCount++;
            cleanupCandidate(candidate, updatedBefore, traceId, counter);
        }
        return counter.result();
    }

    private void cleanupCandidate(Run candidate, Instant updatedBefore, String traceId, Counter counter) {
        Run current = runRepository.findById(candidate.runId()).orElse(candidate);
        if (current.status().isTerminal()
                || current.sourceType() != ConversationSourceType.SIDE_QUESTION
                || !current.updatedAt().isBefore(updatedBefore)) {
            counter.freshSkippedCount++;
            return;
        }

        AgentRuntimeTargetResolver.SessionRuntimeTarget target;
        try {
            // 旁路能力当前固定由 opencode 承载；远端 build 只属于 prompt agent，不是 runtime agentId。
            target = targetResolver.mappedSideQuestionSessionTarget(
                    AgentRuntimeRegistry.DEFAULT_AGENT_ID,
                    current.sessionId(),
                    traceId);
        } catch (RuntimeException mappingFailure) {
            counter.mappingFailedCount++;
            if (missingRemoteSessionMapping(mappingFailure)) {
                // Run 可能在 fork 前重启，也可能落在“远端 fork 已创建、映射尚未保存”的极窄窗口。
                // 两种情况都必须先收敛平台状态；后一种无法跨系统原子恢复，只能留下显式审计日志。
                LOGGER.warn(
                        "event=side_question_orphan_mapping_missing_possible_remote_leak runId={} error={} traceId={}",
                        current.runId().value(),
                        mappingFailure.getClass().getSimpleName(),
                        traceId);
                convergeWithoutRemoteDelete(current, traceId, counter);
                return;
            }
            LOGGER.warn(
                    "event=side_question_orphan_mapping_failed runId={} error={} traceId={}",
                    current.runId().value(),
                    mappingFailure.getClass().getSimpleName(),
                    traceId);
            return;
        }

        try {
            target.runtime().runtime(new AgentRuntimeCommand(
                            target.node(),
                            "DELETE",
                            "/session/" + OpencodeRuntimeApplicationService.encodePathSegment(target.remoteSessionId()),
                            target.directory(),
                            null,
                            Map.of(),
                            Map.of(),
                            traceId))
                    .block();
        } catch (RuntimeException deleteFailure) {
            if (alreadyDeleted(deleteFailure)) {
                LOGGER.info(
                        "event=side_question_orphan_already_deleted runId={} nodeId={} traceId={}",
                        current.runId().value(),
                        target.node().executionNodeId().value(),
                        traceId);
            } else {
                counter.deleteFailedCount++;
                LOGGER.warn(
                        "event=side_question_orphan_delete_failed runId={} nodeId={} error={} traceId={}",
                        current.runId().value(),
                        target.node().executionNodeId().value(),
                        deleteFailure.getClass().getSimpleName(),
                        traceId);
                return;
            }
        }

        if (terminalService.fail(current.runId(), ORPHAN_MESSAGE, traceId)) {
            counter.cleanedCount++;
            LOGGER.info(
                    "event=side_question_orphan_cleaned runId={} nodeId={} traceId={}",
                    current.runId().value(),
                    target.node().executionNodeId().value(),
                    traceId);
        } else {
            counter.casSkippedCount++;
        }
    }

    /**
     * 无远端映射时没有可安全定位的 DELETE 目标，但平台 Run 不能永久保持 active。
     * 终态仍复用同一事务 CAS；若正常执行恰好先完成，CAS 会安全跳过本次清理。
     */
    private void convergeWithoutRemoteDelete(Run current, String traceId, Counter counter) {
        if (terminalService.fail(current.runId(), ORPHAN_MESSAGE, traceId)) {
            counter.cleanedCount++;
        } else {
            counter.casSkippedCount++;
        }
    }

    /** 只把 resolver 明确标记的“尚无映射”视为无 DELETE 目标，其余节点/工作区错误保留到下轮重试。 */
    private boolean missingRemoteSessionMapping(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof PlatformException platformException
                    && platformException.errorCode() == ErrorCode.CONFLICT
                    && "REMOTE_SESSION_MAPPING_MISSING".equals(platformException.details().get("reason"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 远端已不存在等价于幂等 DELETE 成功；facade 会把 HTTP 404 放入平台异常安全 details。 */
    private boolean alreadyDeleted(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof PlatformException platformException) {
                Object status = platformException.details().get("status");
                if (status instanceof Number number && number.intValue() == 404) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /** 单轮清理统计，交给 scheduler 运行记录持久化。 */
    public record Result(
            int scannedCount,
            int freshSkippedCount,
            int mappingFailedCount,
            int deleteFailedCount,
            int casSkippedCount,
            int cleanedCount,
            boolean stopped) {

        /** 转换为稳定的结构化任务结果。 */
        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("scannedCount", scannedCount);
            result.put("freshSkippedCount", freshSkippedCount);
            result.put("mappingFailedCount", mappingFailedCount);
            result.put("deleteFailedCount", deleteFailedCount);
            result.put("casSkippedCount", casSkippedCount);
            result.put("cleanedCount", cleanedCount);
            result.put("stopped", stopped);
            result.put("orphanTimeoutSeconds", ORPHAN_TIMEOUT.toSeconds());
            return Map.copyOf(result);
        }
    }

    private static final class Counter {
        private int scannedCount;
        private int freshSkippedCount;
        private int mappingFailedCount;
        private int deleteFailedCount;
        private int casSkippedCount;
        private int cleanedCount;
        private boolean stopped;

        private Result result() {
            return new Result(
                    scannedCount,
                    freshSkippedCount,
                    mappingFailedCount,
                    deleteFailedCount,
                    casSkippedCount,
                    cleanedCount,
                    stopped);
        }
    }
}
