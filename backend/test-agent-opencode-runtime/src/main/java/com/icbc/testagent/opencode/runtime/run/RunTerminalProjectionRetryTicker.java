package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.scheduler.ScheduledTaskLock;
import com.icbc.testagent.scheduler.ScheduledTaskLockLease;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * 每 5 秒直接从 Redis 唤醒终态投影重试，避免通用 scheduler 的数据库扫描周期把首个 5 秒退避拖长。
 * 多 Java 仍复用同一个 Redis task lock；管理端手工触发的 handler 也使用相同 taskKey，不会并发执行。
 */
@Component
public class RunTerminalProjectionRetryTicker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunTerminalProjectionRetryTicker.class);

    private final RunTerminalProjectionRetryService retryService;
    private final ScheduledTaskLock taskLock;

    public RunTerminalProjectionRetryTicker(
            RunTerminalProjectionRetryService retryService,
            ScheduledTaskLock taskLock) {
        this.retryService = Objects.requireNonNull(retryService, "retryService must not be null");
        this.taskLock = Objects.requireNonNull(taskLock, "taskLock must not be null");
    }

    /** Redis due 查询无命中时不会访问 PostgreSQL；锁或 Redis 故障留待下一轮重试。 */
    @Scheduled(
            fixedDelayString = "${test-agent.redis-summary.terminal-retry-scan-ms:5000}",
            scheduler = RunRuntimeSchedulingConfiguration.MAINTENANCE_SCHEDULER)
    public void tick() {
        ScheduledTaskLockLease lease = null;
        Disposable renewal = null;
        AtomicBoolean lockLost = new AtomicBoolean();
        try {
            lease = taskLock
                    .acquire(
                            RunTerminalProjectionRetryTaskHandler.TASK_KEY,
                            RunTerminalProjectionRetryTaskHandler.LOCK_TTL)
                    .orElse(null);
            if (lease == null) {
                return;
            }
            renewal = renewWhileRunning(lease, lockLost);
            retryService.retryDue(lockLost::get);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Run terminal retry tick deferred, errorType={}",
                    exception.getClass().getSimpleName());
        } finally {
            if (renewal != null) {
                renewal.dispose();
            }
            if (lease != null) {
                try {
                    lease.release();
                } catch (RuntimeException exception) {
                    LOGGER.warn(
                            "Run terminal retry lock release failed, errorType={}",
                            exception.getClass().getSimpleName());
                }
            }
        }
    }

    private Disposable renewWhileRunning(ScheduledTaskLockLease lease, AtomicBoolean lockLost) {
        Duration interval = RunTerminalProjectionRetryTaskHandler.LOCK_TTL.dividedBy(3);
        return Flux.interval(interval).subscribe(
                ignored -> {
                    try {
                        if (!lease.renew()) {
                            lockLost.set(true);
                        }
                    } catch (RuntimeException exception) {
                        lockLost.set(true);
                        LOGGER.warn(
                                "Run terminal retry lock renewal failed, errorType={}",
                                exception.getClass().getSimpleName());
                    }
                },
                error -> {
                    lockLost.set(true);
                    LOGGER.warn(
                            "Run terminal retry renewal scheduler failed, errorType={}",
                            error.getClass().getSimpleName());
                });
    }
}
