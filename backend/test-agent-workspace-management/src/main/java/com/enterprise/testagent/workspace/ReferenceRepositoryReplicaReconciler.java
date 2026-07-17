package com.enterprise.testagent.workspace;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 引用资产库副本补偿器，处理 Redis 广播丢失、Java 节点重启和离线节点重新上线后的补同步。
 */
@Component
public class ReferenceRepositoryReplicaReconciler implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceRepositoryReplicaReconciler.class);

    private final ReferenceRepositoryApplicationService service;
    private final boolean enabled;
    private final Duration interval;
    private volatile ScheduledExecutorService executor;
    private volatile boolean running;

    /** 默认开启并每 60 秒扫描一次；最短间隔限制为 10 秒，避免误配置造成数据库忙轮询。 */
    public ReferenceRepositoryReplicaReconciler(
            ReferenceRepositoryApplicationService service,
            @Value("${test-agent.reference-repository.replica-reconciler.enabled:true}") boolean enabled,
            @Value("${test-agent.reference-repository.replica-reconciler.interval-seconds:60}") long intervalSeconds) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.enabled = enabled;
        this.interval = Duration.ofSeconds(Math.max(10L, intervalSeconds));
    }

    @Override
    public void start() {
        if (running || !enabled) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "reference-repository-replica-reconciler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::safeReconcile, 5L, interval.toSeconds(), TimeUnit.SECONDS);
        running = true;
    }

    @Override
    public void stop() {
        ScheduledExecutorService current = executor;
        if (current != null) {
            current.shutdownNow();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void safeReconcile() {
        try {
            service.reconcileLocalReplicas("trace_" + UUID.randomUUID().toString().replace("-", ""));
        } catch (RuntimeException exception) {
            LOGGER.warn("Reference repository replica reconciliation failed", exception);
        }
    }
}
