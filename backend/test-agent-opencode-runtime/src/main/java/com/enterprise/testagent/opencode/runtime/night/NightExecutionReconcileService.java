package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.run.ScheduledRunMetadata;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 5 分钟补偿：锚点优先，随后按 attempt 租约和精确 Java 心跳做安全恢复。 */
@Service
public class NightExecutionReconcileService {

    private static final int BATCH_SIZE = 50;
    private static final Duration RETENTION = Duration.ofDays(30);

    private final NightExecutionTaskRepository repository;
    private final NightExecutionRunLifecycleService lifecycleService;
    private final BackendJavaRouteResolver routeResolver;
    private final NightExecutionDispatchLeaseGuard leaseGuard;
    private final Clock clock;
    private final Supplier<String> attemptIdSupplier;
    private TransactionTemplate transactionTemplate;

    public NightExecutionReconcileService(
            NightExecutionTaskRepository repository,
            NightExecutionRunLifecycleService lifecycleService,
            BackendJavaRouteResolver routeResolver,
            NightExecutionDispatchLeaseGuard leaseGuard,
            Clock clock) {
        this(repository, lifecycleService, routeResolver, leaseGuard, clock,
                RuntimeIdGenerator::nightExecutionDispatchAttemptId);
    }

    NightExecutionReconcileService(
            NightExecutionTaskRepository repository,
            NightExecutionRunLifecycleService lifecycleService,
            BackendJavaRouteResolver routeResolver,
            NightExecutionDispatchLeaseGuard leaseGuard,
            Clock clock,
            Supplier<String> attemptIdSupplier) {
        this.repository = Objects.requireNonNull(repository);
        this.lifecycleService = Objects.requireNonNull(lifecycleService);
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.leaseGuard = Objects.requireNonNull(leaseGuard);
        this.clock = Objects.requireNonNull(clock);
        this.attemptIdSupplier = Objects.requireNonNull(attemptIdSupplier);
    }

    public Result reconcile(String traceId, BooleanSupplier stopRequested) {
        Instant now = clock.instant();
        int repaired = 0;
        int retried = 0;
        int failed = 0;
        int heartbeatSkipped = 0;
        int cleaned = 0;

        for (NightExecutionTask task : repository.findDispatchingLeaseExpiredBefore(now, BATCH_SIZE)) {
            if (stopRequested.getAsBoolean()) break;
            Optional<Run> anchor = lifecycleService.findAcceptedRun(task);
            if (anchor.isPresent()) {
                lifecycleService.onAccepted(
                        new ScheduledRunMetadata(task.taskId().value(), task.dispatchAttemptId()),
                        anchor.orElseThrow());
                repaired++;
                continue;
            }
            if (ownerStillHandling(task)) {
                // 即使刚过窗口也先等待仍持有 in-flight handle 的 owner；否则旧调用可能在 FAILED 后创建 Run。
                heartbeatSkipped++;
                continue;
            }
            if (!now.isBefore(task.windowEnd())) {
                if (failDispatching(task, now)) failed++;
                continue;
            }
            if (repository.updateDispatchIfAttempt(
                    task.retryDispatch(now), task.dispatchAttemptId())) {
                retried++;
            }
        }

        for (NightExecutionTask task : repository.findScheduledWindowExpired(now, BATCH_SIZE)) {
            if (stopRequested.getAsBoolean()) break;
            Optional<Run> anchor = lifecycleService.findAcceptedRun(task);
            if (anchor.isPresent() && repairScheduledAnchor(task, anchor.orElseThrow(), now)) {
                repaired++;
                continue;
            }
            if (failScheduled(task, now)) failed++;
        }

        for (NightExecutionTask task : repository.findTerminalBefore(now.minus(RETENTION), BATCH_SIZE)) {
            if (stopRequested.getAsBoolean()) break;
            if (repository.deleteTerminalIfUnchanged(
                    task.taskId(), task.stateVersion(), now.minus(RETENTION))) {
                cleaned++;
            }
        }
        if (!stopRequested.getAsBoolean()) {
            repository.deleteReservationsBefore(now.minus(RETENTION));
        }
        return new Result(repaired, retried, failed, heartbeatSkipped, cleaned);
    }

    private boolean repairScheduledAnchor(NightExecutionTask task, Run run, Instant now) {
        String attemptId = attemptIdSupplier.get();
        NightExecutionTask claimed = task.startDispatch(
                attemptId,
                routeResolver.currentBackendProcessIdValue(),
                now.plus(NightExecutionDispatchLeaseGuard.LEASE_DURATION),
                now);
        if (!repository.claimForDispatch(claimed, task.targetLinuxServerId())) return false;
        lifecycleService.onAccepted(new ScheduledRunMetadata(task.taskId().value(), attemptId), run);
        return true;
    }

    private boolean ownerStillHandling(NightExecutionTask task) {
        BackendProcessId owner;
        try {
            owner = new BackendProcessId(task.dispatchOwnerBackendProcessId());
        } catch (RuntimeException invalidOwner) {
            return false;
        }
        if (routeResolver.isCurrent(owner)) {
            // 当前 Java 能看到自己的 in-flight guard；租约过期且 handle 已消失即可由本机恢复。
            return leaseGuard.isInFlight(task.taskId(), task.dispatchAttemptId());
        }
        try {
            routeResolver.requireBackend(owner);
            return true;
        } catch (RuntimeException offline) {
            return false;
        }
    }

    private boolean failDispatching(NightExecutionTask task, Instant now) {
        return inTransaction(() -> {
            NightExecutionTask failed = task.fail("WINDOW_EXPIRED", "夜间执行窗口内未能启动", now);
            if (!repository.updateDispatchIfAttempt(failed, task.dispatchAttemptId())) return false;
            release(task, now);
            return true;
        });
    }

    private boolean failScheduled(NightExecutionTask task, Instant now) {
        return inTransaction(() -> {
            NightExecutionTask failed = task.fail("WINDOW_EXPIRED", "夜间执行窗口内未能启动", now);
            if (!repository.updateIfStatus(failed, NightExecutionTaskStatus.SCHEDULED)) return false;
            release(task, now);
            return true;
        });
    }

    private void release(NightExecutionTask task, Instant now) {
        repository.deleteSessionLock(task.sessionId(), task.taskId());
        repository.releaseSlot(task.slotStart(), now);
    }

    @Autowired(required = false)
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private <T> T inTransaction(Supplier<T> action) {
        if (transactionTemplate == null) return action.get();
        return Objects.requireNonNull(transactionTemplate.execute(status -> action.get()));
    }

    public record Result(int repaired, int retried, int failed, int heartbeatSkipped, int cleaned) {
        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("repaired", repaired);
            values.put("retried", retried);
            values.put("failed", failed);
            values.put("heartbeatSkipped", heartbeatSkipped);
            values.put("cleaned", cleaned);
            return values;
        }
    }
}
