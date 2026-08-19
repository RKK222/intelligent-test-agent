package com.enterprise.testagent.workspace;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 应用版本共享副本的本机有界任务调度器。HTTP 请求和 Redis 广播只负责登记任务，Git clone/fetch
 * 统一在后台线程执行，避免慢仓库阻塞请求线程；同一应用工作空间版本在运行或排队时会合并重复唤醒。
 */
@Component
public class ManagedWorkspaceReplicaTaskDispatcher implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedWorkspaceReplicaTaskDispatcher.class);

    private final int workerCount;
    private final int maxPendingTasks;
    private final Object monitor = new Object();
    private final Set<String> enqueuedTaskKeys = new HashSet<>();
    private volatile ThreadPoolExecutor executor;
    private volatile boolean running;

    /** 默认允许两个不同版本并行，等待队列最多保留 256 个应用工作空间版本。 */
    public ManagedWorkspaceReplicaTaskDispatcher(
            @Value("${test-agent.managed-workspace.replica-worker.worker-count:2}") int workerCount,
            @Value("${test-agent.managed-workspace.replica-worker.max-pending-tasks:256}") int maxPendingTasks) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (maxPendingTasks < 1) {
            throw new IllegalArgumentException("maxPendingTasks must be positive");
        }
        this.workerCount = workerCount;
        this.maxPendingTasks = maxPendingTasks;
    }

    /**
     * 提交本机共享副本任务；同 key 已运行或排队视为幂等成功，队列未启动或已满时返回 false。
     */
    public boolean dispatch(String taskKey, String traceId, Runnable task) {
        String normalizedTaskKey = Objects.requireNonNull(taskKey, "taskKey must not be null");
        Objects.requireNonNull(task, "task must not be null");
        synchronized (monitor) {
            ThreadPoolExecutor current = executor;
            if (!running || current == null || current.isShutdown()) {
                return false;
            }
            if (enqueuedTaskKeys.contains(normalizedTaskKey)) {
                return true;
            }
            if (enqueuedTaskKeys.size() >= maxPendingTasks) {
                logRejected(normalizedTaskKey, traceId);
                return false;
            }
            enqueuedTaskKeys.add(normalizedTaskKey);
            try {
                current.execute(() -> execute(normalizedTaskKey, traceId, task));
                return true;
            } catch (RejectedExecutionException exception) {
                enqueuedTaskKeys.remove(normalizedTaskKey);
                logRejected(normalizedTaskKey, traceId);
                return false;
            }
        }
    }

    private void execute(String taskKey, String traceId, Runnable task) {
        LOGGER.info("event=managed_workspace_replica_task_started taskKey={} traceId={}", taskKey, traceId);
        boolean succeeded = false;
        long startedAt = System.nanoTime();
        try {
            task.run();
            succeeded = true;
        } catch (RuntimeException exception) {
            // 业务任务已经把脱敏失败状态写回副本；这里仅记录任务边界，不重复输出可能含物理路径的异常。
            LOGGER.warn("event=managed_workspace_replica_task_failed taskKey={} traceId={}", taskKey, traceId);
        } finally {
            synchronized (monitor) {
                enqueuedTaskKeys.remove(taskKey);
            }
            LOGGER.info(
                    "event=managed_workspace_replica_task_completed taskKey={} result={} durationMs={} traceId={}",
                    taskKey,
                    succeeded ? "SUCCESS" : "FAILED",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                    traceId);
        }
    }

    private void logRejected(String taskKey, String traceId) {
        LOGGER.warn("event=managed_workspace_replica_task_rejected taskKey={} traceId={}", taskKey, traceId);
    }

    @Override
    public void start() {
        synchronized (monitor) {
            if (running) {
                return;
            }
            executor = new ThreadPoolExecutor(
                    workerCount,
                    workerCount,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(maxPendingTasks),
                    runnable -> {
                        Thread thread = new Thread(runnable, "managed-workspace-replica-worker");
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy());
            running = true;
        }
    }

    @Override
    public void stop() {
        ThreadPoolExecutor current;
        synchronized (monitor) {
            running = false;
            enqueuedTaskKeys.clear();
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
}
