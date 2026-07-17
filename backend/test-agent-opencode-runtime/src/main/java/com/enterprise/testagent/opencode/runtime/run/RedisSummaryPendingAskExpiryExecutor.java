package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.agent.runtime.AgentCancelCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.run.RunOwnerLease;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.event.RunEventAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 等待 permission/question 满 7 天后，按 fencing lease 取消远端并收敛 Redis/数据库终态。 */
@Component
@Order(0)
public class RedisSummaryPendingAskExpiryExecutor implements RunPendingAskExpiryExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSummaryPendingAskExpiryExecutor.class);
    private static final Duration CANCEL_TIMEOUT = Duration.ofSeconds(10);

    private final RunRuntimeStore runtimeStore;
    private final AgentRuntimeRegistry runtimes;
    private final RunRecoveryExecutionNodeResolver nodes;
    private final RunOwnerLeaseSupervisor leases;
    private final RunEventAppender eventAppender;
    private final RunTerminalProjectionService terminalProjection;
    private final Clock clock;

    public RedisSummaryPendingAskExpiryExecutor(
            RunRuntimeStore runtimeStore,
            AgentRuntimeRegistry runtimes,
            RunRecoveryExecutionNodeResolver nodes,
            RunOwnerLeaseSupervisor leases,
            RunEventAppender eventAppender,
            RunTerminalProjectionService terminalProjection,
            Clock clock) {
        this.runtimeStore = Objects.requireNonNull(runtimeStore, "runtimeStore must not be null");
        this.runtimes = Objects.requireNonNull(runtimes, "runtimes must not be null");
        this.nodes = Objects.requireNonNull(nodes, "nodes must not be null");
        this.leases = Objects.requireNonNull(leases, "leases must not be null");
        this.eventAppender = Objects.requireNonNull(eventAppender, "eventAppender must not be null");
        this.terminalProjection = Objects.requireNonNull(
                terminalProjection, "terminalProjection must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public boolean expirePendingAsk(
            RunRuntimeManifest manifest,
            RunOwnerLease lease,
            String traceId) {
        if (!manifest.active()
                || manifest.attention() == null
                || !manifest.runId().equals(lease.runId())) {
            return false;
        }
        return terminate(
                manifest,
                lease,
                traceId,
                RunStatus.CANCELLED,
                "PENDING_ASK_EXPIRED",
                "PENDING_ASK_EXPIRED",
                "等待用户处理已超过 7 天，当前对话已安全终止");
    }

    /** 复用同一 fencing-safe 终止程序处理 pending 7 天和普通无活动 2 小时两种系统收敛。 */
    boolean terminate(
            RunRuntimeManifest manifest,
            RunOwnerLease lease,
            String traceId,
            RunStatus terminalStatus,
            String terminalSource,
            String terminalReasonCode,
            String safeMessage) {
        if (!manifest.active()
                || (terminalStatus != RunStatus.CANCELLED && terminalStatus != RunStatus.FAILED)
                || !manifest.runId().equals(lease.runId())) {
            return false;
        }
        Optional<RunRuntimeInput> input = runtimeStore.findInput(manifest.runId());
        Optional<ExecutionNode> node = input.flatMap(value ->
                nodes.resolve(manifest.executionNodeId(), value.executionNodeBaseUrl()));
        if (input.map(RunRuntimeInput::workspaceRootPath).filter(path -> !path.isBlank()).isEmpty()
                || node.isEmpty()) {
            return false;
        }
        Optional<RunOwnerLeaseSupervisor.OwnershipHandle> adopted = leases.adopt(lease);
        if (adopted.isEmpty()) {
            return false;
        }
        RunOwnerLeaseSupervisor.OwnershipHandle ownership = adopted.orElseThrow();
        try {
            leases.requireOwned(ownership);
            boolean remoteStopConfirmed = cancelBestEffort(
                    manifest,
                    input.orElseThrow(),
                    node.orElseThrow(),
                    runtimes.require(manifest.agentId()),
                    traceId);
            leases.requireOwned(ownership);
            Instant now = clock.instant();
            RunEventType terminalEventType = terminalStatus == RunStatus.CANCELLED
                    ? RunEventType.RUN_CANCELLED
                    : RunEventType.RUN_FAILED;
            eventAppender.append(new RunEventDraft(
                    manifest.runId(),
                    terminalEventType,
                    traceId,
                    now,
                    RunTerminalProjectionOutboxPayload.payload(
                            Map.of(
                                    "status", terminalStatus.name(),
                                    "reason", terminalReasonCode),
                            terminalSource,
                            terminalReasonCode,
                            safeMessage,
                            remoteStopConfirmed)),
                    RunStorageMode.REDIS_SUMMARY,
                    ownership.lease());
            // fenced terminal append 已原子封闭接管窗口，关系型 CAS 失败则进入安全重试队列。
            terminalProjection.project(
                    manifest.runId(),
                    terminalStatus,
                    terminalSource,
                    terminalReasonCode,
                    safeMessage,
                    remoteStopConfirmed,
                    traceId);
            return true;
        } finally {
            try {
                leases.release(ownership);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Pending ask expiry lease release deferred, runId={}, errorType={}, traceId={}",
                        manifest.runId().value(),
                        exception.getClass().getSimpleName(),
                        traceId);
            }
        }
    }

    private boolean cancelBestEffort(
            RunRuntimeManifest manifest,
            RunRuntimeInput input,
            ExecutionNode node,
            AgentRuntime runtime,
            String traceId) {
        try {
            if (manifest.rootRemoteSessionId() == null || manifest.rootRemoteSessionId().isBlank()) {
                return false;
            }
            var result = runtime.cancelSession(new AgentCancelCommand(
                            node,
                            manifest.rootRemoteSessionId(),
                            input.workspaceRootPath(),
                            null,
                            traceId))
                    .block(CANCEL_TIMEOUT);
            return result != null && result.cancelled();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Pending ask expiry remote cancel failed, runId={}, errorType={}, traceId={}",
                    manifest.runId().value(),
                    exception.getClass().getSimpleName(),
                    traceId);
            return false;
        }
    }
}
