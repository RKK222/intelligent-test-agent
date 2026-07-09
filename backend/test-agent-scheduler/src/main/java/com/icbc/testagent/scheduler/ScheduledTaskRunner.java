package com.icbc.testagent.scheduler;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.scheduler.ScheduledTask;
import com.icbc.testagent.domain.scheduler.ScheduledTaskPlan;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRepository;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRun;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.icbc.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.icbc.testagent.observability.TraceIdSupport;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 定时任务后台 runner，单线程扫描 due task 和 pending manual run，实际互斥由 Redis 锁保证。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ScheduledTaskRunner implements SmartLifecycle, ApplicationRunner, ScheduledTaskDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTaskRunner.class);

    private final ScheduledTaskRepository repository;
    private final ScheduledTaskRegistry registry;
    private final ScheduledTaskLock lock;
    private final CronScheduleCalculator cronScheduleCalculator;
    private final SchedulerProperties properties;
    private final Clock clock;
    private final Object monitor = new Object();
    private final ScheduledExecutorService renewalExecutor = Executors.newSingleThreadScheduledExecutor(
            namedThreadFactory("test-agent-scheduler-lock-renewal"));

    private volatile boolean running;
    private volatile Instant lastScanStartedAt;
    private volatile Instant lastScanFinishedAt;
    private volatile String lastScanErrorMessage;
    private Thread scanThread;

    /**
     * 注入调度所需端口；handler 查找和注册同步由 ScheduledTaskRegistry 负责。
     */
    public ScheduledTaskRunner(
            ScheduledTaskRepository repository,
            ScheduledTaskRegistry registry,
            ScheduledTaskLock lock,
            CronScheduleCalculator cronScheduleCalculator,
            SchedulerProperties properties,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.lock = Objects.requireNonNull(lock, "lock must not be null");
        this.cronScheduleCalculator = Objects.requireNonNull(cronScheduleCalculator, "cronScheduleCalculator must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 先同步代码注册任务，便于管理页展示；默认关闭时只是不启动后台扫描线程。
     */
    @Override
    public void start() {
        registry.syncRegisteredTasks(TraceIdSupport.generate());
        if (!properties.isEnabled() || running) {
            return;
        }
        running = true;
        scanThread = namedThreadFactory("test-agent-scheduler-scan").newThread(this::scanLoop);
        scanThread.start();
    }

    @Override
    public void stop() {
        running = false;
        wakeUp();
        renewalExecutor.shutdownNow();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean runnerRunning() {
        return running;
    }

    @Override
    public Instant lastScanStartedAt() {
        return lastScanStartedAt;
    }

    @Override
    public Instant lastScanFinishedAt() {
        return lastScanFinishedAt;
    }

    @Override
    public String lastScanErrorMessage() {
        return lastScanErrorMessage;
    }

    @Override
    public boolean isAutoStartup() {
        return false;
    }

    /**
     * 通过 ApplicationRunner 启动扫描，确保 app 模块的 Flyway migration 先完成。
     */
    @Override
    public void run(ApplicationArguments args) {
        start();
    }

    /**
     * 唤醒扫描线程，用于管理员手动触发后尽快执行 pending run。
     */
    @Override
    public void wakeUp() {
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    /**
     * 执行单轮扫描，供单元测试和后台循环复用。
     */
    public void scanOnce() {
        lastScanStartedAt = clock.instant();
        Instant now = clock.instant();
        try {
            scanDueTasks(now);
            scanPendingManualRuns(now);
            lastScanErrorMessage = null;
        } catch (RuntimeException exception) {
            lastScanErrorMessage = exception.getMessage();
            throw exception;
        } finally {
            lastScanFinishedAt = clock.instant();
        }
    }

    private void scanLoop() {
        while (running) {
            try {
                scanOnce();
            } catch (Exception exception) {
                LOGGER.warn("Scheduler scan failed", exception);
            }
            waitNextScan();
        }
    }

    private void waitNextScan() {
        synchronized (monitor) {
            try {
                monitor.wait(properties.getScanInterval().toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void scanDueTasks(Instant now) {
        for (ScheduledTask task : repository.findDueTasks(now, properties.getDueTaskLimit())) {
            ScheduledTaskRun run = ScheduledTaskRun.pending(
                    new ScheduledTaskRunId(RuntimeIdGenerator.scheduledTaskRunId()),
                    task.taskKey(),
                    null,
                    ScheduledTaskTriggerType.CRON,
                    null,
                    task.nextFireAt(),
                    TraceIdSupport.generate());
            Optional<ScheduledTaskRun> activeRun = repository.findActiveRunByTaskKey(task.taskKey());
            if (activeRun.isPresent()) {
                repository.saveRun(run.skip(activeSkipReason(activeRun.get()), now));
                advanceNextFireAt(task, now);
                continue;
            }
            ScheduledTaskRun saved = repository.saveRun(run);
            processPendingRun(task, saved, true);
            advanceNextFireAt(task, now);
        }
    }

    private void scanPendingManualRuns(Instant now) {
        for (ScheduledTaskRun run : repository.findPendingRuns(
                ScheduledTaskTriggerType.MANUAL,
                now,
                properties.getManualRunLimit())) {
            Optional<ScheduledTask> task = repository.findTaskByKey(run.taskKey());
            if (task.isEmpty()) {
                repository.saveRun(run.fail(ErrorCode.NOT_FOUND.name(), "定时任务不存在", now));
                continue;
            }
            processPendingRun(task.get(), run, false);
        }
    }

    private void processPendingRun(ScheduledTask task, ScheduledTaskRun pendingRun, boolean skipOnLockFailure) {
        if (pendingRun.status() != ScheduledTaskRunStatus.PENDING) {
            return;
        }
        Instant now = clock.instant();
        Optional<ScheduledTaskRun> activeRun = repository.findActiveRunByTaskKeyExcluding(task.taskKey(), pendingRun.taskRunId());
        if (activeRun.isPresent()) {
            repository.saveRun(pendingRun.skip(activeSkipReason(activeRun.get()), now));
            return;
        }
        Optional<ScheduledTaskLockLease> lease = lock.acquire(task.taskKey(), task.lockTtl());
        if (lease.isEmpty()) {
            if (skipOnLockFailure) {
                repository.saveRun(pendingRun.skip("未获取 Redis 分布式锁", now));
            }
            return;
        }
        ScheduledTaskLockLease acquiredLease = lease.get();
        ScheduledFuture<?> renewalFuture = scheduleRenewal(acquiredLease);
        ScheduledTaskRun runningRun = repository.saveRun(pendingRun.start(properties.getInstanceId(), clock.instant()));
        try {
            ScheduledTaskHandler handler = registry.handlerFor(task.taskKey())
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.NOT_FOUND,
                            "定时任务 handler 未注册",
                            Map.of("taskKey", task.taskKey().value())));
            ScheduledTaskResult result = handler.run(contextFor(runningRun));
            if (stopRequested(runningRun.taskRunId())) {
                repository.saveRun(latestRunOr(runningRun).manuallyStopped(clock.instant()));
            } else {
                repository.saveRun(runningRun.succeed(result == null ? Map.of() : result.result(), clock.instant()));
            }
        } catch (ScheduledTaskStopRequestedException exception) {
            repository.saveRun(latestRunOr(runningRun).manuallyStopped(clock.instant()));
        } catch (PlatformException exception) {
            repository.saveRun(runningRun.fail(exception.errorCode().name(), exception.getMessage(), clock.instant()));
        } catch (Exception exception) {
            repository.saveRun(runningRun.fail(ErrorCode.INTERNAL_ERROR.name(), "定时任务执行失败", clock.instant()));
            LOGGER.warn("Scheduled task handler failed, taskKey={}, taskRunId={}",
                    task.taskKey().value(),
                    runningRun.taskRunId().value(),
                    exception);
        } finally {
            if (renewalFuture != null) {
                renewalFuture.cancel(true);
            }
            acquiredLease.release();
        }
    }

    private ScheduledTaskContext contextFor(ScheduledTaskRun run) {
        Map<String, Object> payload = run.planId() == null
                ? Map.of()
                : repository.findPlanById(run.planId()).map(ScheduledTaskPlan::payload).orElse(Map.of());
        return new ScheduledTaskContext(
                run.taskRunId(),
                run.taskKey(),
                run.planId(),
                run.triggerType(),
                run.requestedByUserId(),
                run.scheduledFireAt(),
                run.traceId(),
                payload,
                () -> stopRequested(run.taskRunId()));
    }

    private boolean stopRequested(ScheduledTaskRunId taskRunId) {
        return repository.findRunById(taskRunId)
                .map(run -> run.status() == ScheduledTaskRunStatus.STOPPING || run.stopRequestedAt() != null)
                .orElse(false);
    }

    private ScheduledTaskRun latestRunOr(ScheduledTaskRun fallback) {
        return repository.findRunById(fallback.taskRunId()).orElse(fallback);
    }

    private ScheduledFuture<?> scheduleRenewal(ScheduledTaskLockLease lease) {
        long ttlMillis = lease.ttl().toMillis();
        long periodMillis = Math.max(1000L, ttlMillis / 3);
        return renewalExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        if (!lease.renew()) {
                            LOGGER.warn("Failed to renew scheduler lock, lockKey={}", lease.lockKey());
                        }
                    } catch (Exception exception) {
                        LOGGER.warn("Failed to renew scheduler lock, lockKey={}", lease.lockKey(), exception);
                    }
                },
                periodMillis,
                periodMillis,
                TimeUnit.MILLISECONDS);
    }

    private void advanceNextFireAt(ScheduledTask task, Instant now) {
        try {
            repository.saveTask(task.withNextFireAt(cronScheduleCalculator.nextFireAt(task.cronExpression(), now), now));
        } catch (PlatformException exception) {
            LOGGER.warn("Failed to advance scheduler nextFireAt, taskKey={}", task.taskKey().value(), exception);
        }
    }

    private String activeSkipReason(ScheduledTaskRun activeRun) {
        return "同一 taskKey 已有未结束运行：" + activeRun.taskRunId().value();
    }

    private static ThreadFactory namedThreadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
