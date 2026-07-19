package com.enterprise.testagent.workspace;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 引用资产副本的本机有界调度器。Redis、本机请求和补偿扫描只负责提交任务，Git 阻塞操作统一在后台线程执行。
 */
@Component
public class ReferenceRepositoryReplicaTaskDispatcher implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceRepositoryReplicaTaskDispatcher.class);
    private static final long RUNNING_SUCCESSOR_RECHECK_MILLIS = 10L;

    private final int workerCount;
    private final int maxPendingTasks;
    private final Clock clock;
    private final Object monitor = new Object();
    private final Map<TaskKey, TaskSlot> taskSlots = new HashMap<>();
    private volatile ScheduledThreadPoolExecutor executor;
    private volatile boolean running;

    /** 生产默认同时执行两个仓库任务，并限制最多 256 个不同仓库 generation 等待执行。 */
    @Autowired
    public ReferenceRepositoryReplicaTaskDispatcher(
            @Value("${test-agent.reference-repository.replica-worker.worker-count:2}") int workerCount,
            @Value("${test-agent.reference-repository.replica-worker.max-pending-tasks:256}") int maxPendingTasks) {
        this(workerCount, maxPendingTasks, Clock.systemUTC());
    }

    /** 测试构造器允许缩小并发和容量并固定时钟。 */
    ReferenceRepositoryReplicaTaskDispatcher(int workerCount, int maxPendingTasks, Clock clock) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (maxPendingTasks < 1) {
            throw new IllegalArgumentException("maxPendingTasks must be positive");
        }
        this.workerCount = workerCount;
        this.maxPendingTasks = maxPendingTasks;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void start() {
        synchronized (monitor) {
            if (running) {
                return;
            }
            ScheduledThreadPoolExecutor created = new ScheduledThreadPoolExecutor(workerCount, runnable -> {
                Thread thread = new Thread(runnable, "reference-repository-replica-worker");
                thread.setDaemon(true);
                return thread;
            });
            created.setRemoveOnCancelPolicy(true);
            executor = created;
            running = true;
        }
    }

    @Override
    public void stop() {
        ScheduledThreadPoolExecutor current;
        synchronized (monitor) {
            running = false;
            for (TaskSlot slot : taskSlots.values()) {
                if (slot.scheduled != null && slot.scheduled.future != null) {
                    slot.scheduled.future.cancel(false);
                }
            }
            taskSlots.clear();
            current = executor;
            executor = null;
        }
        if (current != null) {
            current.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 立即提交一个副本任务；相同仓库 generation 已运行或等待时视为幂等成功。 */
    public boolean dispatchNow(
            CodeRepositoryId repositoryId,
            long generation,
            String traceId,
            WakeSource source,
            Runnable task) {
        return dispatchAt(repositoryId, generation, traceId, source, clock.instant(), task);
    }

    /**
     * 在指定时刻提交副本任务；同 key 的更早唤醒会替换已有延迟任务，运行中的即时重复唤醒直接合并。
     */
    public boolean dispatchAt(
            CodeRepositoryId repositoryId,
            long generation,
            String traceId,
            WakeSource source,
            Instant notBefore,
            Runnable task) {
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(notBefore, "notBefore must not be null");
        Objects.requireNonNull(task, "task must not be null");
        TaskKey key = new TaskKey(repositoryId, generation);
        Instant now = clock.instant();
        Instant scheduledFor = notBefore.isBefore(now) ? now : notBefore;
        synchronized (monitor) {
            ScheduledThreadPoolExecutor current = executor;
            if (!running || current == null || current.isShutdown()) {
                return false;
            }
            TaskSlot slot = taskSlots.get(key);
            if (slot == null) {
                if (taskSlots.size() >= maxPendingTasks) {
                    logRejected(key, traceId, source);
                    return false;
                }
                slot = new TaskSlot();
                taskSlots.put(key, slot);
            } else if (slot.running && !scheduledFor.isAfter(now)) {
                return true;
            } else if (slot.scheduled != null && !scheduledFor.isBefore(slot.scheduled.scheduledFor)) {
                return true;
            }
            if (slot.scheduled != null && slot.scheduled.future != null) {
                slot.scheduled.future.cancel(false);
            }
            ScheduledTask registration = new ScheduledTask(scheduledFor, now, traceId, source, task);
            slot.scheduled = registration;
            try {
                registration.future = current.schedule(
                        () -> execute(key, registration),
                        delayMillis(now, scheduledFor),
                        TimeUnit.MILLISECONDS);
                return true;
            } catch (RejectedExecutionException exception) {
                slot.scheduled = null;
                if (!slot.running) {
                    taskSlots.remove(key);
                }
                logRejected(key, traceId, source);
                return false;
            }
        }
    }

    private void execute(TaskKey key, ScheduledTask registration) {
        synchronized (monitor) {
            TaskSlot slot = taskSlots.get(key);
            if (!running || slot == null || slot.scheduled != registration) {
                return;
            }
            if (slot.running) {
                ScheduledThreadPoolExecutor current = executor;
                if (current == null || current.isShutdown()) {
                    return;
                }
                try {
                    registration.future = current.schedule(
                            () -> execute(key, registration),
                            RUNNING_SUCCESSOR_RECHECK_MILLIS,
                            TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException exception) {
                    slot.scheduled = null;
                }
                return;
            }
            slot.scheduled = null;
            slot.running = true;
        }

        Instant startedAt = clock.instant();
        long queueWaitMillis = elapsedMillis(registration.enqueuedAt, startedAt);
        LOGGER.info(
                "event=reference_repository_replica_task_started repositoryId={} generation={} source={} queueWaitMs={} traceId={}",
                key.repositoryId().value(), key.generation(), registration.source, queueWaitMillis, registration.traceId);
        boolean succeeded = false;
        try {
            registration.task.run();
            succeeded = true;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "event=reference_repository_replica_task_failed repositoryId={} generation={} source={} traceId={}",
                    key.repositoryId().value(), key.generation(), registration.source, registration.traceId);
        } finally {
            LOGGER.info(
                    "event=reference_repository_replica_task_completed repositoryId={} generation={} source={} result={} durationMs={} traceId={}",
                    key.repositoryId().value(), key.generation(), registration.source,
                    succeeded ? "SUCCESS" : "FAILED", elapsedMillis(startedAt, clock.instant()), registration.traceId);
            synchronized (monitor) {
                TaskSlot slot = taskSlots.get(key);
                if (slot != null) {
                    slot.running = false;
                    if (slot.scheduled == null) {
                        taskSlots.remove(key);
                    }
                }
            }
        }
    }

    private void logRejected(TaskKey key, String traceId, WakeSource source) {
        LOGGER.warn(
                "event=reference_repository_replica_task_rejected repositoryId={} generation={} source={} traceId={}",
                key.repositoryId().value(), key.generation(), source, traceId);
    }

    private long delayMillis(Instant now, Instant scheduledFor) {
        return Math.max(0L, Duration.between(now, scheduledFor).toMillis());
    }

    private long elapsedMillis(Instant startedAt, Instant completedAt) {
        return Math.max(0L, Duration.between(startedAt, completedAt).toMillis());
    }

    /** 记录唤醒来源，便于区分本机请求、跨实例广播、补偿扫描和定向重试。 */
    public enum WakeSource {
        LOCAL_REQUEST,
        SERVER_BROADCAST,
        RECONCILIATION,
        RETRY
    }

    private record TaskKey(CodeRepositoryId repositoryId, long generation) {
    }

    private static final class TaskSlot {
        private boolean running;
        private ScheduledTask scheduled;
    }

    private static final class ScheduledTask {
        private final Instant scheduledFor;
        private final Instant enqueuedAt;
        private final String traceId;
        private final WakeSource source;
        private final Runnable task;
        private ScheduledFuture<?> future;

        private ScheduledTask(
                Instant scheduledFor,
                Instant enqueuedAt,
                String traceId,
                WakeSource source,
                Runnable task) {
            this.scheduledFor = scheduledFor;
            this.enqueuedAt = enqueuedAt;
            this.traceId = traceId;
            this.source = source;
            this.task = task;
        }
    }
}
