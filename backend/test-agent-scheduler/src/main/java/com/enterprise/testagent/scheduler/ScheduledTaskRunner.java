package com.enterprise.testagent.scheduler;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.scheduler.ScheduledTask;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskPlan;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRepository;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRun;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.observability.TraceIdSupport;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ScheduledTaskExecutionAffinityProvider affinityProvider;
    private final ThreadPoolExecutor userPlanExecutor;
    private final Set<ScheduledTaskRunId> queuedUserPlanRuns = java.util.concurrent.ConcurrentHashMap.newKeySet();
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
        this(repository, registry, lock, cronScheduleCalculator, properties, clock, () -> null);
    }

    /** 生产装配注入当前 Linux 服务器亲和键。 */
    @Autowired
    public ScheduledTaskRunner(
            ScheduledTaskRepository repository,
            ScheduledTaskRegistry registry,
            ScheduledTaskLock lock,
            CronScheduleCalculator cronScheduleCalculator,
            SchedulerProperties properties,
            Clock clock,
            ScheduledTaskExecutionAffinityProvider affinityProvider) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.lock = Objects.requireNonNull(lock, "lock must not be null");
        this.cronScheduleCalculator = Objects.requireNonNull(cronScheduleCalculator, "cronScheduleCalculator must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.affinityProvider = Objects.requireNonNull(affinityProvider, "affinityProvider must not be null");
        this.userPlanExecutor = new ThreadPoolExecutor(
                properties.getUserPlanWorkerCount(),
                properties.getUserPlanWorkerCount(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getUserPlanQueueCapacity()),
                namedThreadFactory("test-agent-scheduler-user-plan"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 只同步 USER_PLAN 任务；周期与手工任务已由 XXL-JOB/MySQL 管理。
     */
    @Override
    public void start() {
        registry.syncUserPlanTasks(TraceIdSupport.generate());
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
        userPlanExecutor.shutdownNow();
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
            scanPendingUserPlanRuns(now);
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

    private void scanPendingUserPlanRuns(Instant now) {
        String affinity = affinityProvider.currentAffinity();
        if (affinity == null || affinity.isBlank()) {
            return;
        }
        for (ScheduledTaskRun run : repository.findPendingRuns(
                ScheduledTaskTriggerType.USER_PLAN,
                affinity.trim(),
                now,
                properties.getUserPlanRunLimit())) {
            if (!queuedUserPlanRuns.add(run.taskRunId())) {
                continue;
            }
            try {
                userPlanExecutor.execute(() -> {
                    try {
                        Optional<ScheduledTask> task = repository.findTaskByKey(run.taskKey());
                        if (task.isEmpty()) {
                            repository.updateRunIfStatus(
                                    run.fail(ErrorCode.NOT_FOUND.name(), "定时任务不存在", clock.instant()),
                                    ScheduledTaskRunStatus.PENDING);
                            return;
                        }
                        processPendingRun(task.get(), run, false);
                    } finally {
                        queuedUserPlanRuns.remove(run.taskRunId());
                    }
                });
            } catch (RejectedExecutionException exception) {
                queuedUserPlanRuns.remove(run.taskRunId());
                LOGGER.debug("USER_PLAN worker 队列已满，保留待执行记录 taskRunId={}", run.taskRunId().value());
                return;
            }
        }
    }

    private void processPendingRun(ScheduledTask task, ScheduledTaskRun pendingRun, boolean skipOnLockFailure) {
        if (pendingRun.status() != ScheduledTaskRunStatus.PENDING) {
            return;
        }
        Instant now = clock.instant();
        if (pendingRun.triggerType() != ScheduledTaskTriggerType.USER_PLAN) {
            Optional<ScheduledTaskRun> activeRun = repository.findActiveRunByTaskKeyExcluding(task.taskKey(), pendingRun.taskRunId());
            if (activeRun.isPresent()) {
                repository.saveRun(pendingRun.skip(activeSkipReason(activeRun.get()), now));
                return;
            }
        }
        ScheduledTaskKey lockIdentity = pendingRun.triggerType() == ScheduledTaskTriggerType.USER_PLAN
                ? new ScheduledTaskKey("run." + pendingRun.taskRunId().value())
                : task.taskKey();
        Optional<ScheduledTaskLockLease> lease = lock.acquire(lockIdentity, task.lockTtl());
        if (lease.isEmpty()) {
            if (skipOnLockFailure) {
                repository.saveRun(pendingRun.skip("未获取 Redis 分布式锁", now));
            }
            return;
        }
        ScheduledTaskLockLease acquiredLease = lease.get();
        ScheduledTaskRun latestPending = repository.findRunById(pendingRun.taskRunId()).orElse(pendingRun);
        if (latestPending.status() != ScheduledTaskRunStatus.PENDING) {
            acquiredLease.release();
            return;
        }
        ScheduledTaskRun runningRun = latestPending.start(properties.getInstanceId(), clock.instant());
        if (!repository.updateRunIfStatus(runningRun, ScheduledTaskRunStatus.PENDING)) {
            acquiredLease.release();
            return;
        }
        ScheduledFuture<?> renewalFuture = scheduleRenewal(acquiredLease);
        try {
            ScheduledTaskHandler handler = registry.handlerFor(task.taskKey())
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.NOT_FOUND,
                            "定时任务 handler 未注册",
                            Map.of("taskKey", task.taskKey().value())));
            if (!handler.supportedTriggerTypes().contains(runningRun.triggerType())) {
                throw new PlatformException(ErrorCode.CONFLICT, "定时任务 handler 不支持当前触发类型");
            }
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

    /** 测试与有序关闭使用：等待 USER_PLAN worker 队列清空。 */
    public boolean awaitUserPlanIdle(java.time.Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (queuedUserPlanRuns.isEmpty() && userPlanExecutor.getActiveCount() == 0) return true;
            try {
                Thread.sleep(5L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return queuedUserPlanRuns.isEmpty() && userPlanExecutor.getActiveCount() == 0;
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
