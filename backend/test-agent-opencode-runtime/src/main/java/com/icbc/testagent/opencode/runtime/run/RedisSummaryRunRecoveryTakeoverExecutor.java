package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.AgentStreamEventsCommand;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventScopeContext;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.event.RunSessionScopeRepository;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.run.RunOwnerLease;
import com.icbc.testagent.domain.run.RunRuntimeInput;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.event.RunEventAppender;
import java.util.Objects;
import java.util.Optional;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 实际恢复已接收 Run 的 OpenCode SSE 订阅；任何路径都不会调用 startRun/prompt_async。 */
@Component
@Order(0)
public class RedisSummaryRunRecoveryTakeoverExecutor implements RunRecoveryTakeoverExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSummaryRunRecoveryTakeoverExecutor.class);

    private final RunRuntimeStore runtimeStore;
    private final AgentRuntimeRegistry runtimes;
    private final RunRecoveryExecutionNodeResolver nodes;
    private final RunOwnerLeaseSupervisor leases;
    private final RunSessionScopeRouter scopeRouter;
    private final RunEventAppender eventAppender;
    private final RunEventPersistencePolicy persistencePolicy;
    private final RunTerminalProjectionService terminalProjection;
    private final RunRuntimeLossConvergenceScheduler runtimeLossScheduler;

    /** 兼容既有单元测试构造器。 */
    RedisSummaryRunRecoveryTakeoverExecutor(
            RunRuntimeStore runtimeStore,
            AgentRuntimeRegistry runtimes,
            RunRecoveryExecutionNodeResolver nodes,
            RunOwnerLeaseSupervisor leases,
            RunSessionScopeRepository scopeRepository,
            RunSessionScopeRuntimeCache scopeCache,
            RunEventAppender eventAppender,
            RunEventPersistencePolicy persistencePolicy,
            RunTerminalProjectionService terminalProjection) {
        this(
                runtimeStore,
                runtimes,
                nodes,
                leases,
                scopeRepository,
                scopeCache,
                eventAppender,
                persistencePolicy,
                terminalProjection,
                null);
    }

    /** 生产构造器接入 Redis 连续中断 30 秒后的安全取消与终态收敛。 */
    @Autowired
    RedisSummaryRunRecoveryTakeoverExecutor(
            RunRuntimeStore runtimeStore,
            AgentRuntimeRegistry runtimes,
            RunRecoveryExecutionNodeResolver nodes,
            RunOwnerLeaseSupervisor leases,
            RunSessionScopeRepository scopeRepository,
            RunSessionScopeRuntimeCache scopeCache,
            RunEventAppender eventAppender,
            RunEventPersistencePolicy persistencePolicy,
            RunTerminalProjectionService terminalProjection,
            RunRuntimeLossConvergenceScheduler runtimeLossScheduler) {
        this.runtimeStore = Objects.requireNonNull(runtimeStore, "runtimeStore must not be null");
        this.runtimes = Objects.requireNonNull(runtimes, "runtimes must not be null");
        this.nodes = Objects.requireNonNull(nodes, "nodes must not be null");
        this.leases = Objects.requireNonNull(leases, "leases must not be null");
        this.scopeRouter = new RunSessionScopeRouter(scopeRepository, scopeCache, runtimeStore);
        this.eventAppender = Objects.requireNonNull(eventAppender, "eventAppender must not be null");
        this.persistencePolicy = Objects.requireNonNull(persistencePolicy, "persistencePolicy must not be null");
        this.terminalProjection = Objects.requireNonNull(terminalProjection, "terminalProjection must not be null");
        this.runtimeLossScheduler = runtimeLossScheduler;
    }

    @Override
    public boolean resumeAcceptedRun(RunRuntimeManifest manifest, RunOwnerLease lease, String traceId) {
        if (!manifest.active() || !manifest.runId().equals(lease.runId())) {
            return false;
        }
        Optional<RunRuntimeInput> input = runtimeStore.findInput(manifest.runId());
        Optional<ExecutionNode> node = input.flatMap(value -> nodes.resolve(
                manifest.executionNodeId(), value.executionNodeBaseUrl()));
        if (node.isEmpty() || input.map(RunRuntimeInput::workspaceRootPath).filter(path -> !path.isBlank()).isEmpty()) {
            return false;
        }
        Optional<RunOwnerLeaseSupervisor.OwnershipHandle> adopted = leases.adopt(lease);
        if (adopted.isEmpty()) {
            return false;
        }
        RunOwnerLeaseSupervisor.OwnershipHandle ownership = adopted.orElseThrow();
        AgentRuntime runtime = runtimes.require(manifest.agentId());
        RunEventScopeContext rootScope = RunEventScopeContext.root(manifest.runId(), manifest.rootRemoteSessionId());
        runtime.streamRunEvents(new AgentStreamEventsCommand(
                        node.orElseThrow(),
                        manifest.runId(),
                        manifest.rootRemoteSessionId(),
                        input.orElseThrow().workspaceRootPath(),
                        null,
                        traceId))
                .concatMap(draft -> Mono.fromCallable(() -> {
                            leases.requireOwned(ownership);
                            return scopeRouter.route(
                                    rootScope,
                                    draft,
                                    RunStorageMode.REDIS_SUMMARY,
                                    ownership.lease());
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable))
                .takeUntil(this::terminal)
                .takeUntilOther(ownership.lost())
                .concatMap(draft -> Mono.fromRunnable(() -> appendFenced(manifest, ownership, draft))
                        .subscribeOn(Schedulers.boundedElastic()))
                .doOnError(error -> {
                    if (!scheduleRuntimeLoss(
                            manifest,
                            input.orElseThrow(),
                            runtime,
                            node.orElseThrow(),
                            traceId,
                            error)) {
                        LOGGER.warn(
                                "恢复 Run SSE 订阅中断，等待周期扫描再次接管，runId={}, traceId={}, exceptionType={}",
                                manifest.runId().value(), traceId, error.getClass().getSimpleName());
                    }
                })
                .doFinally(ignored -> {
                    scopeRouter.finishRun(manifest.runId());
                    try {
                        leases.release(ownership);
                    } catch (RuntimeException releaseError) {
                        LOGGER.warn("恢复 Run owner lease 释放失败，等待 TTL，runId={}, traceId={}, exceptionType={}",
                                manifest.runId().value(), traceId, releaseError.getClass().getSimpleName());
                    }
                })
                .subscribe(ignored -> { }, ignored -> { });
        return true;
    }

    /** OpenCode 明确未接收 dispatch 时只做失败收敛，禁止恢复路径重新发送 prompt。 */
    @Override
    public boolean failUnacceptedRun(RunRuntimeManifest manifest, RunOwnerLease lease, String traceId) {
        if (!manifest.active() || !manifest.runId().equals(lease.runId())) {
            return false;
        }
        Optional<RunOwnerLeaseSupervisor.OwnershipHandle> adopted = leases.adopt(lease);
        if (adopted.isEmpty()) {
            return false;
        }
        RunOwnerLeaseSupervisor.OwnershipHandle ownership = adopted.orElseThrow();
        try {
            leases.requireOwned(ownership);
            Instant occurredAt = Instant.now();
            RunEventDraft failed = new RunEventDraft(
                    manifest.runId(),
                    RunEventType.RUN_FAILED,
                    traceId,
                    occurredAt,
                    Map.of(
                            "terminalSource", "RECOVERY_DISPATCH_PROBE",
                            "terminalReasonCode", "DISPATCH_NOT_ACCEPTED",
                            "safeErrorMessage", "远端未接收本次请求，对话已安全终止",
                            "remoteStopConfirmed", false));
            eventAppender.append(
                    persistencePolicy.sanitizeForPersistence(failed),
                    RunStorageMode.REDIS_SUMMARY,
                    ownership.lease());
            terminalProjection.project(
                    manifest.runId(),
                    RunStatus.FAILED,
                    "RECOVERY_DISPATCH_PROBE",
                    "DISPATCH_NOT_ACCEPTED",
                    "远端未接收本次请求，对话已安全终止",
                    false,
                    traceId);
            return true;
        } finally {
            try {
                leases.release(ownership);
            } catch (RuntimeException releaseError) {
                LOGGER.warn(
                        "未接收 Run owner lease 释放失败，等待 TTL，runId={}, traceId={}, exceptionType={}",
                        manifest.runId().value(), traceId, releaseError.getClass().getSimpleName());
            }
        }
    }

    private boolean scheduleRuntimeLoss(
            RunRuntimeManifest manifest,
            RunRuntimeInput input,
            AgentRuntime runtime,
            ExecutionNode node,
            String traceId,
            Throwable error) {
        if (runtimeLossScheduler == null
                || manifest.userId() == null
                || !runtimeStateUnavailable(error)) {
            return false;
        }
        runtimeLossScheduler.schedule(
                new RunRuntimeLossRequest(
                        manifest.runId(),
                        manifest.sessionId(),
                        manifest.userId(),
                        manifest.agentId(),
                        manifest.dispatchMessageId(),
                        manifest.rootRemoteSessionId(),
                        input.workspaceRootPath(),
                        ConversationSourceType.MANUAL,
                        null,
                        traceId),
                runtime,
                node);
        return true;
    }

    private boolean runtimeStateUnavailable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof com.icbc.testagent.common.error.PlatformException platform
                    && (platform.errorCode() == com.icbc.testagent.common.error.ErrorCode.RUNTIME_STATE_UNAVAILABLE
                    || platform.errorCode() == com.icbc.testagent.common.error.ErrorCode.RUN_DETAILS_EXPIRED)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void appendFenced(
            RunRuntimeManifest manifest,
            RunOwnerLeaseSupervisor.OwnershipHandle ownership,
            RunEventDraft draft) {
        leases.requireOwned(ownership);
        if (terminal(draft)) {
            RunStatus status = draft.type() == RunEventType.RUN_SUCCEEDED
                    ? RunStatus.SUCCEEDED
                    : (draft.type() == RunEventType.RUN_CANCELLED ? RunStatus.CANCELLED : RunStatus.FAILED);
            RunEventDraft sanitized = persistencePolicy.sanitizeForPersistence(
                    RunTerminalProjectionOutboxPayload.enrich(
                            draft,
                            "RECOVERY_REMOTE_ROOT",
                            status.name(),
                            null,
                            false));
            eventAppender.append(sanitized, RunStorageMode.REDIS_SUMMARY, ownership.lease());
            // Redis 已以 fencing token 原子接受终态，后续条件接管会被拒绝，DB CAS 可继续完成。
            terminalProjection.project(
                    manifest.runId(), status, "RECOVERY_REMOTE_ROOT", status.name(), null, false, draft.traceId());
            return;
        }
        RunEventDraft sanitized = persistencePolicy.sanitizeForPersistence(draft);
        if (persistencePolicy.shouldPersist(draft)) {
            eventAppender.append(sanitized, RunStorageMode.REDIS_SUMMARY, ownership.lease());
        } else {
            eventAppender.publishTransient(sanitized, RunStorageMode.REDIS_SUMMARY, ownership.lease());
        }
    }

    private boolean terminal(RunEventDraft draft) {
        return draft.type() == RunEventType.RUN_SUCCEEDED
                || draft.type() == RunEventType.RUN_FAILED
                || draft.type() == RunEventType.RUN_CANCELLED;
    }
}
