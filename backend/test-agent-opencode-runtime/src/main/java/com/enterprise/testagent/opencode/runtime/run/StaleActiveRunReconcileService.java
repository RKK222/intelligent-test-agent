package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.event.RunEventAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 收敛长时间无活动的 active Run，修复后端订阅失效后历史会话长期显示运行中的平台状态。
 */
@Service
public class StaleActiveRunReconcileService {

    public static final String TIMEOUT_MESSAGE = "运行超时，后台订阅已失效或长时间无输出";

    static final Duration ACTIVE_TIMEOUT = Duration.ofHours(2);
    static final int SCAN_LIMIT = 200;

    private static final Logger LOGGER = LoggerFactory.getLogger(StaleActiveRunReconcileService.class);

    private final RunRepository runRepository;
    private final RunEventAppender runEventAppender;
    private final RunActivityStateStore activityStateStore;
    private final RunSessionMessageSnapshotService snapshotService;
    private final Clock clock;

    /**
     * 注入收敛所需端口。snapshotService 可为空，便于纯单测验证状态机和事件追加。
     */
    @Autowired
    public StaleActiveRunReconcileService(
            RunRepository runRepository,
            RunEventAppender runEventAppender,
            RunActivityStateStore activityStateStore,
            RunSessionMessageSnapshotService snapshotService,
            Clock clock) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.runEventAppender = Objects.requireNonNull(runEventAppender, "runEventAppender must not be null");
        this.activityStateStore = activityStateStore == null ? new RunActivityStateStore(null) : activityStateStore;
        this.snapshotService = snapshotService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 扫描超过超时阈值的 active Run；Redis 有近期输出或未处理 ask 时保守跳过。
     */
    public Result reconcile(String traceId, BooleanSupplier stopRequested) {
        Objects.requireNonNull(traceId, "traceId must not be null");
        BooleanSupplier shouldStop = stopRequested == null ? () -> false : stopRequested;
        Instant now = clock.instant();
        Instant updatedBefore = now.minus(ACTIVE_TIMEOUT);
        ResultCounter counter = new ResultCounter();
        for (Run candidate : runRepository.findStaleActiveRuns(updatedBefore, SCAN_LIMIT)) {
            if (shouldStop.getAsBoolean()) {
                break;
            }
            counter.scannedCount++;
            reconcileCandidate(candidate, updatedBefore, now, traceId, counter);
        }
        return counter.toResult();
    }

    private void reconcileCandidate(
            Run candidate,
            Instant updatedBefore,
            Instant now,
            String traceId,
            ResultCounter counter) {
        Run current = runRepository.findById(candidate.runId()).orElse(candidate);
        if (current.status().isTerminal() || !current.updatedAt().isBefore(updatedBefore)) {
            counter.freshSkippedCount++;
            return;
        }
        if (hasPendingAsk(current, counter)) {
            return;
        }
        if (hasRecentOutput(current, counter)) {
            return;
        }

        Run failed = current.fail(now);
        Run saved = runRepository.saveIfStatus(failed, current.status());
        if (saved != failed) {
            counter.casSkippedCount++;
            LOGGER.info(
                    "Skipped stale active Run timeout because CAS failed, runId={}, expectedStatus={}, actualStatus={}, traceId={}",
                    current.runId().value(),
                    current.status().name(),
                    saved.status().name(),
                    traceId);
            return;
        }
        runEventAppender.append(new RunEventDraft(
                saved.runId(),
                RunEventType.RUN_FAILED,
                traceId,
                now,
                timeoutPayload()));
        activityStateStore.clearRunState(saved.runId());
        persistSnapshotBestEffort(saved, traceId);
        counter.failedCount++;
        LOGGER.warn(
                "Marked stale active Run as FAILED, runId={}, activeTimeoutSeconds={}, traceId={}",
                saved.runId().value(),
                ACTIVE_TIMEOUT.toSeconds(),
                traceId);
    }

    private boolean hasPendingAsk(Run run, ResultCounter counter) {
        try {
            if (activityStateStore.hasPendingAsk(run.runId())) {
                counter.pendingAskSkippedCount++;
                return true;
            }
            return false;
        } catch (RuntimeException exception) {
            counter.pendingAskSkippedCount++;
            LOGGER.warn("Skipped stale active Run because pending ask lookup failed, runId={}", run.runId().value(), exception);
            return true;
        }
    }

    private boolean hasRecentOutput(Run run, ResultCounter counter) {
        try {
            if (activityStateStore.hasRecentOutput(run.runId())) {
                counter.recentOutputSkippedCount++;
                return true;
            }
            return false;
        } catch (RuntimeException exception) {
            counter.recentOutputSkippedCount++;
            LOGGER.warn("Skipped stale active Run because output activity lookup failed, runId={}", run.runId().value(), exception);
            return true;
        }
    }

    private void persistSnapshotBestEffort(Run run, String traceId) {
        if (snapshotService == null) {
            return;
        }
        try {
            String agentId = run.agentId() == null ? AgentRuntimeRegistry.DEFAULT_AGENT_ID : run.agentId();
            snapshotService.persistRunSnapshot(agentId, run, traceId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to refresh stale timeout Run snapshot, runId={}, traceId={}",
                    run.runId().value(), traceId, exception);
        }
    }

    private Map<String, Object> timeoutPayload() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", TIMEOUT_MESSAGE);
        payload.put("error", Map.of(
                "name", "StaleActiveRunTimeout",
                "message", TIMEOUT_MESSAGE));
        payload.put("reason", "STALE_ACTIVE_RUN_TIMEOUT");
        payload.put("activeTimeoutSeconds", ACTIVE_TIMEOUT.toSeconds());
        payload.put("recentOutputWindowSeconds", RunActivityStateStore.OUTPUT_ACTIVITY_TTL.toSeconds());
        return Map.copyOf(payload);
    }

    /**
     * 单轮收敛结果，写入 scheduler run result_json 供管理员排障。
     */
    public record Result(
            int scannedCount,
            int freshSkippedCount,
            int recentOutputSkippedCount,
            int pendingAskSkippedCount,
            int casSkippedCount,
            int failedCount) {

        /**
         * 转成结构化 Map，避免 scheduler 层了解业务 record 结构。
         */
        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("scannedCount", scannedCount);
            result.put("freshSkippedCount", freshSkippedCount);
            result.put("recentOutputSkippedCount", recentOutputSkippedCount);
            result.put("pendingAskSkippedCount", pendingAskSkippedCount);
            result.put("casSkippedCount", casSkippedCount);
            result.put("failedCount", failedCount);
            result.put("activeTimeoutSeconds", ACTIVE_TIMEOUT.toSeconds());
            result.put("recentOutputWindowSeconds", RunActivityStateStore.OUTPUT_ACTIVITY_TTL.toSeconds());
            return Map.copyOf(result);
        }
    }

    private static final class ResultCounter {
        private int scannedCount;
        private int freshSkippedCount;
        private int recentOutputSkippedCount;
        private int pendingAskSkippedCount;
        private int casSkippedCount;
        private int failedCount;

        private Result toResult() {
            return new Result(
                    scannedCount,
                    freshSkippedCount,
                    recentOutputSkippedCount,
                    pendingAskSkippedCount,
                    casSkippedCount,
                    failedCount);
        }
    }
}
