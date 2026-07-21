package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTask;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.opencode.runtime.run.ScheduledRunMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 当前 owner Java 的轻量分发 watchdog。
 *
 * <p>跨进程补偿发现 owner 心跳在线时不能抢占；本类只收敛本进程已过期且没有 in-flight handle 的 attempt，
 * 不排队、不启动 Run，也不承担夜间任务执行。
 */
@Service
public class NightExecutionDispatchOwnerWatchdog {

    private static final int BATCH_SIZE = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(NightExecutionDispatchOwnerWatchdog.class);

    private final NightExecutionTaskRepository repository;
    private final NightExecutionRunLifecycleService lifecycleService;
    private final NightExecutionDispatchLeaseGuard leaseGuard;
    private final BackendInstanceIdentity backendIdentity;
    private final Clock clock;

    public NightExecutionDispatchOwnerWatchdog(
            NightExecutionTaskRepository repository,
            NightExecutionRunLifecycleService lifecycleService,
            NightExecutionDispatchLeaseGuard leaseGuard,
            BackendInstanceIdentity backendIdentity,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.lifecycleService = Objects.requireNonNull(lifecycleService);
        this.leaseGuard = Objects.requireNonNull(leaseGuard);
        this.backendIdentity = Objects.requireNonNull(backendIdentity);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 每分钟先查 Run 锚点，再按 attempt fencing 恢复本机遗留认领。 */
    @Scheduled(fixedDelay = 60_000L)
    void reconcileOwnedAttempts() {
        Instant now = clock.instant();
        List<NightExecutionTask> owned;
        try {
            owned = repository.findDispatchingByOwner(backendIdentity.backendProcessId(), BATCH_SIZE);
        } catch (RuntimeException failure) {
            LOGGER.warn("夜间任务 owner watchdog 扫描失败，exceptionType={}",
                    failure.getClass().getSimpleName());
            return;
        }
        for (NightExecutionTask task : owned) {
            if (task.dispatchLeaseUntil().isAfter(now)
                    || leaseGuard.isInFlight(task.taskId(), task.dispatchAttemptId())) {
                continue;
            }
            try {
                Optional<Run> anchor = lifecycleService.findAcceptedRun(task);
                if (anchor.isPresent()) {
                    lifecycleService.onAccepted(
                            new ScheduledRunMetadata(task.taskId().value(), task.dispatchAttemptId()),
                            anchor.orElseThrow());
                    continue;
                }
                repository.updateDispatchIfAttempt(
                        task.retryDispatch(now), task.dispatchAttemptId());
            } catch (RuntimeException failure) {
                LOGGER.warn("夜间任务 owner watchdog 收敛失败，taskId={}, exceptionType={}",
                        task.taskId().value(), failure.getClass().getSimpleName());
            }
        }
    }
}
