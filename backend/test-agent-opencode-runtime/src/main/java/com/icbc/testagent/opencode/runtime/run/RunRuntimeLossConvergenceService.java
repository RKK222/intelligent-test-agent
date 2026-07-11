package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.agent.runtime.AgentCancelCommand;
import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.run.RunConversationSummary;
import com.icbc.testagent.domain.run.RunDiffCounts;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunSummaryPersistencePort;
import com.icbc.testagent.domain.run.RunSummaryStatus;
import com.icbc.testagent.domain.run.RunTerminalProjection;
import com.icbc.testagent.domain.run.RunTerminalProjectionResult;
import com.icbc.testagent.domain.run.RunTerminalRetry;
import com.icbc.testagent.domain.run.RunTerminalRetryStore;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.opencode.runtime.run.summary.RunConversationSummarizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Redis 运行态连续中断后的安全收敛程序。
 *
 * <p>调用方负责在首次 Redis 故障后等待固定 grace；本服务再次探测 manifest，只有仍不可用时才尽力取消远端，
 * 并直接用启动时缓存的安全控制面字段写 FAILED 终态。整个路径不读取 Redis input、Stream 或 snapshot。
 */
@Service
public class RunRuntimeLossConvergenceService {

    /** Redis 短暂抖动不会触发远端取消，调用方必须等待该固定窗口后再调用。 */
    public static final Duration GRACE_PERIOD = Duration.ofSeconds(30);

    private static final Duration REMOTE_CANCEL_TIMEOUT = Duration.ofSeconds(10);
    private static final long INITIAL_ANCHOR_STATUS_VERSION = 1L;
    private static final String RUNTIME_STATE_LOST = "RUNTIME_STATE_LOST";
    private static final String SAFE_ERROR_MESSAGE = "运行态不可用，当前对话已安全终止";
    private static final String USER_FALLBACK = "用户请求摘要不可用";
    private static final String ASSISTANT_FALLBACK = "助手回答摘要不可用";
    private static final Logger LOGGER = LoggerFactory.getLogger(RunRuntimeLossConvergenceService.class);

    private final RunRuntimeStore runtimeStore;
    private final RunSummaryPersistencePort persistencePort;
    private final RunTerminalRetryStore retryStore;
    private final Clock clock;

    /** 兼容既有手工构造；生产装配使用带安全重试端口的构造器。 */
    public RunRuntimeLossConvergenceService(
            RunRuntimeStore runtimeStore,
            RunSummaryPersistencePort persistencePort) {
        this(runtimeStore, persistencePort, null, Clock.systemUTC());
    }

    /** 生产构造注入 Redis 安全重试端口；grace 的调度由调用方完成。 */
    @Autowired
    public RunRuntimeLossConvergenceService(
            RunRuntimeStore runtimeStore,
            RunSummaryPersistencePort persistencePort,
            RunTerminalRetryStore retryStore,
            Clock clock) {
        this.runtimeStore = Objects.requireNonNull(runtimeStore, "runtimeStore must not be null");
        this.persistencePort = Objects.requireNonNull(persistencePort, "persistencePort must not be null");
        this.retryStore = retryStore;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** 测试构造允许固定时间，避免详情到期时间和摘要创建时间产生抖动。 */
    RunRuntimeLossConvergenceService(
            RunRuntimeStore runtimeStore,
            RunSummaryPersistencePort persistencePort,
            Clock clock) {
        this(runtimeStore, persistencePort, null, clock);
    }

    /**
     * 在 30 秒 grace 到期后收敛一次 Run。Redis 恢复时立即返回；Redis 仍不可用时取消远端并写安全终态。
     */
    public RunRuntimeLossConvergenceResult converge(
            RunRuntimeLossRequest request,
            AgentRuntime runtime,
            ExecutionNode executionNode) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(runtime, "runtime must not be null");
        Objects.requireNonNull(executionNode, "executionNode must not be null");
        Optional<RunRuntimeManifest> manifest = redisManifest(request);
        if (manifest.filter(RunRuntimeManifest::active).isPresent()) {
            return new RunRuntimeLossConvergenceResult(
                    RunRuntimeLossConvergenceResult.Outcome.RUNTIME_RECOVERED,
                    false,
                    false);
        }

        RunRuntimeManifest recoveredTerminal = manifest
                .filter(candidate -> candidate.status().isTerminal())
                .orElse(null);
        boolean remoteCancellationAttempted = recoveredTerminal == null && request.remoteSessionId() != null;
        boolean remoteStopConfirmed = remoteCancellationAttempted
                && cancelRemoteBestEffort(request, runtime, executionNode);
        RunTerminalProjection projection = recoveredTerminal == null
                ? safeProjection(request, remoteStopConfirmed, clock.instant())
                : recoveredTerminalProjection(request, recoveredTerminal, clock.instant());
        try {
            RunTerminalProjectionResult result = persistencePort.persistTerminal(projection);
            RunRuntimeLossConvergenceResult.Outcome outcome = switch (result) {
                case APPLIED -> RunRuntimeLossConvergenceResult.Outcome.TERMINAL_APPLIED;
                case VERSION_CONFLICT -> RunRuntimeLossConvergenceResult.Outcome.TERMINAL_VERSION_CONFLICT;
                case TERMINAL_PENDING_DB -> RunRuntimeLossConvergenceResult.Outcome.TERMINAL_PENDING_DB;
            };
            return new RunRuntimeLossConvergenceResult(
                    outcome, remoteCancellationAttempted, remoteStopConfirmed);
        } catch (RuntimeException exception) {
            // 数据库异常消息可能包含连接信息，因此这里只记录异常类型和安全控制面 ID。
            LOGGER.warn(
                    "Redis 运行态丢失后的终态事务失败，runId={}, exceptionType={}, traceId={}",
                    request.runId().value(),
                    exception.getClass().getSimpleName(),
                    request.traceId());
            if (retryStore != null) {
                try {
                    retryStore.save(RunTerminalRetry.pending(projection, clock.instant()));
                    return new RunRuntimeLossConvergenceResult(
                            RunRuntimeLossConvergenceResult.Outcome.TERMINAL_PENDING_DB,
                            remoteCancellationAttempted,
                            remoteStopConfirmed);
                } catch (RuntimeException retryFailure) {
                    LOGGER.warn(
                            "Redis 运行态丢失后的安全终态重试入队失败，runId={}, exceptionType={}, traceId={}",
                            request.runId().value(),
                            retryFailure.getClass().getSimpleName(),
                            request.traceId());
                }
            }
            return new RunRuntimeLossConvergenceResult(
                    RunRuntimeLossConvergenceResult.Outcome.TERMINAL_PERSISTENCE_FAILED,
                    remoteCancellationAttempted,
                    remoteStopConfirmed);
        }
    }

    private Optional<RunRuntimeManifest> redisManifest(RunRuntimeLossRequest request) {
        try {
            return runtimeStore.findManifest(request.runId());
        } catch (RuntimeException exception) {
            // 当前调用只关心 Redis 是否恢复；异常详情不写日志，避免客户端数据透过驱动异常被带出。
            LOGGER.warn(
                    "Redis 运行态在 grace 到期后仍不可用，runId={}, exceptionType={}, traceId={}",
                    request.runId().value(),
                    exception.getClass().getSimpleName(),
                    request.traceId());
            return Optional.empty();
        }
    }

    private boolean cancelRemoteBestEffort(
            RunRuntimeLossRequest request,
            AgentRuntime runtime,
            ExecutionNode executionNode) {
        try {
            var result = runtime.cancelSession(new AgentCancelCommand(
                            executionNode,
                            request.remoteSessionId(),
                            request.workspaceRoot(),
                            null,
                            request.traceId()))
                    .block(REMOTE_CANCEL_TIMEOUT);
            return result != null && result.cancelled();
        } catch (RuntimeException exception) {
            // 取消失败不能阻止平台终态收敛；不记录异常消息，避免远端响应正文进入日志。
            LOGGER.warn(
                    "Redis 运行态丢失后的远端取消失败，runId={}, exceptionType={}, traceId={}",
                    request.runId().value(),
                    exception.getClass().getSimpleName(),
                    request.traceId());
            return false;
        }
    }

    private RunTerminalProjection safeProjection(
            RunRuntimeLossRequest request,
            boolean remoteStopConfirmed,
            Instant now) {
        return new RunTerminalProjection(
                request.runId(),
                request.sessionId(),
                RunStatus.FAILED,
                INITIAL_ANCHOR_STATUS_VERSION,
                RUNTIME_STATE_LOST,
                RUNTIME_STATE_LOST,
                SAFE_ERROR_MESSAGE,
                remoteStopConfirmed,
                0L,
                now,
                request.remoteSessionId(),
                RunDiffCounts.empty(),
                null,
                null,
                TokenUsage.empty(),
                null,
                request.traceId(),
                now,
                request.agentId(),
                request.sourceType(),
                request.sourceRefId(),
                request.userId(),
                fallbackSummaries(request, now));
    }

    /**
     * Redis 已恢复但 manifest 已终态时，Java 可能恰好在终态 Lua 与 PostgreSQL 事务之间退出。
     * 不能把它当作继续运行；保留 Redis 终态和安全计数，以 fallback 双摘要幂等补齐关系型锚点。
     */
    private RunTerminalProjection recoveredTerminalProjection(
            RunRuntimeLossRequest request,
            RunRuntimeManifest manifest,
            Instant now) {
        return new RunTerminalProjection(
                request.runId(),
                request.sessionId(),
                manifest.status(),
                INITIAL_ANCHOR_STATUS_VERSION,
                "REDIS_TERMINAL_RECOVERY",
                "RUNTIME_STATE_LOST_AFTER_TERMINAL",
                manifest.status() == RunStatus.FAILED ? SAFE_ERROR_MESSAGE : null,
                false,
                manifest.lastSeq(),
                manifest.detailsExpiresAt(),
                manifest.rootRemoteSessionId() == null
                        ? request.remoteSessionId()
                        : manifest.rootRemoteSessionId(),
                RunDiffCounts.empty(),
                null,
                null,
                TokenUsage.empty(),
                null,
                request.traceId(),
                now,
                request.agentId(),
                request.sourceType(),
                request.sourceRefId(),
                request.userId(),
                fallbackSummaries(request, now));
    }

    private List<RunConversationSummary> fallbackSummaries(RunRuntimeLossRequest request, Instant now) {
        int version = RunConversationSummarizer.SUMMARY_VERSION;
        return List.of(
                new RunConversationSummary(
                        RunSummaryIdentifiers.user(request.runId(), request.dispatchMessageId()),
                        SessionMessageRole.USER,
                        USER_FALLBACK,
                        summaryKey(request, SessionMessageRole.USER, version),
                        version,
                        RunSummaryStatus.FALLBACK,
                        now,
                        null),
                new RunConversationSummary(
                        RunSummaryIdentifiers.assistant(request.runId()),
                        SessionMessageRole.ASSISTANT,
                        ASSISTANT_FALLBACK,
                        summaryKey(request, SessionMessageRole.ASSISTANT, version),
                        version,
                        RunSummaryStatus.FALLBACK,
                        now,
                        null));
    }

    private String summaryKey(
            RunRuntimeLossRequest request,
            SessionMessageRole role,
            int version) {
        return request.runId().value() + ':' + role.name() + ":v" + version;
    }
}
