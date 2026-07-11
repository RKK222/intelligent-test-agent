package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.run.RunId;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 对首次 Redis 运行态错误做 30 秒延迟与单 Run 去重；延迟任务只捕获安全控制面快照，不保存 prompt 或事件。
 */
@Service
public class RunRuntimeLossConvergenceScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunRuntimeLossConvergenceScheduler.class);
    private static final List<Duration> RETRY_BACKOFF = List.of(
            Duration.ofSeconds(5),
            Duration.ofSeconds(15),
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5));

    private final RunRuntimeLossConvergenceService convergenceService;
    private final Scheduler delayScheduler;
    private final Set<RunId> pending = ConcurrentHashMap.newKeySet();

    public RunRuntimeLossConvergenceScheduler(RunRuntimeLossConvergenceService convergenceService) {
        this(convergenceService, Schedulers.parallel());
    }

    /** 测试构造允许使用虚拟时钟验证精确 30 秒边界。 */
    RunRuntimeLossConvergenceScheduler(
            RunRuntimeLossConvergenceService convergenceService,
            Scheduler delayScheduler) {
        this.convergenceService = Objects.requireNonNull(
                convergenceService, "convergenceService must not be null");
        this.delayScheduler = Objects.requireNonNull(delayScheduler, "delayScheduler must not be null");
    }

    /** 同一 Run 的并发 Redis 错误只保留一个 grace 计时器。 */
    public boolean schedule(
            RunRuntimeLossRequest request,
            AgentRuntime runtime,
            ExecutionNode executionNode) {
        return schedule(request, runtime, executionNode, null);
    }

    /**
     * 启动阶段尚未派发 prompt 时，Redis 若在 grace 内恢复，调用方仍需用新 fencing owner 主动关闭该 Run；
     * 普通事件流故障不传回调，恢复后继续由运行态接管。
     */
    public boolean schedule(
            RunRuntimeLossRequest request,
            AgentRuntime runtime,
            ExecutionNode executionNode,
            Runnable runtimeRecoveredAction) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(runtime, "runtime must not be null");
        Objects.requireNonNull(executionNode, "executionNode must not be null");
        if (!pending.add(request.runId())) {
            return false;
        }
        scheduleAttempt(
                request,
                runtime,
                executionNode,
                runtimeRecoveredAction,
                RunRuntimeLossConvergenceService.GRACE_PERIOD,
                0);
        return true;
    }

    /**
     * PostgreSQL 与 Redis 同时不可用时没有可写的持久化介质，因此 JVM 内必须保留重试任务，直到任一终态
     * 结果成功落入 PostgreSQL 或 Redis retry/outbox。最后一级按 5 分钟持续重试，不会静默丢任务。
     */
    private void scheduleAttempt(
            RunRuntimeLossRequest request,
            AgentRuntime runtime,
            ExecutionNode executionNode,
            Runnable runtimeRecoveredAction,
            Duration delay,
            int retryIndex) {
        Mono.delay(delay, delayScheduler)
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        ignored -> {
                            if (attemptConvergence(
                                    request, runtime, executionNode, runtimeRecoveredAction)) {
                                pending.remove(request.runId());
                                return;
                            }
                            Duration nextDelay = RETRY_BACKOFF.get(
                                    Math.min(retryIndex, RETRY_BACKOFF.size() - 1));
                            LOGGER.warn(
                                    "Redis runtime loss convergence will retry, runId={}, retryDelaySeconds={}, traceId={}",
                                    request.runId().value(),
                                    nextDelay.toSeconds(),
                                    request.traceId());
                            scheduleAttempt(
                                    request,
                                    runtime,
                                    executionNode,
                                    runtimeRecoveredAction,
                                    nextDelay,
                                    retryIndex + 1);
                        },
                        error -> {
                            LOGGER.warn(
                                    "Redis runtime loss convergence timer failed, runId={}, errorType={}, traceId={}",
                                    request.runId().value(),
                                    error.getClass().getSimpleName(),
                                    request.traceId());
                            Duration nextDelay = RETRY_BACKOFF.get(
                                    Math.min(retryIndex, RETRY_BACKOFF.size() - 1));
                            scheduleAttempt(
                                    request,
                                    runtime,
                                    executionNode,
                                    runtimeRecoveredAction,
                                    nextDelay,
                                    retryIndex + 1);
                        });
    }

    private boolean attemptConvergence(
            RunRuntimeLossRequest request,
            AgentRuntime runtime,
            ExecutionNode executionNode,
            Runnable runtimeRecoveredAction) {
        try {
            RunRuntimeLossConvergenceResult result =
                    convergenceService.converge(request, runtime, executionNode);
            if (result.outcome()
                    == RunRuntimeLossConvergenceResult.Outcome.TERMINAL_PERSISTENCE_FAILED) {
                return false;
            }
            if (runtimeRecoveredAction != null
                    && result.outcome()
                    == RunRuntimeLossConvergenceResult.Outcome.RUNTIME_RECOVERED) {
                try {
                    runtimeRecoveredAction.run();
                } catch (RuntimeException callbackFailure) {
                    // Redis 可能在恢复回调中再次中断；立即重做安全收敛探测，避免留下永久 RUNNING 锚点。
                    LOGGER.warn(
                            "Redis runtime recovered action failed, runId={}, errorType={}, traceId={}",
                            request.runId().value(),
                            callbackFailure.getClass().getSimpleName(),
                            request.traceId());
                    RunRuntimeLossConvergenceResult retryResult =
                            convergenceService.converge(request, runtime, executionNode);
                    return retryResult.outcome()
                            != RunRuntimeLossConvergenceResult.Outcome.TERMINAL_PERSISTENCE_FAILED
                            && retryResult.outcome()
                            != RunRuntimeLossConvergenceResult.Outcome.RUNTIME_RECOVERED;
                }
            }
            return true;
        } catch (RuntimeException error) {
            LOGGER.warn(
                    "Redis runtime loss convergence deferred, runId={}, errorType={}, traceId={}",
                    request.runId().value(),
                    error.getClass().getSimpleName(),
                    request.traceId());
            return false;
        }
    }
}
