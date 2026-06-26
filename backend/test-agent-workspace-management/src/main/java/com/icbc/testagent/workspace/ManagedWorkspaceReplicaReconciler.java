package com.icbc.testagent.workspace;

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
 * 应用版本工作区副本补偿器，弥补 Redis pub/sub 漏消息或节点重启期间错过的同步事件。
 */
@Component
public class ManagedWorkspaceReplicaReconciler implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedWorkspaceReplicaReconciler.class);

    private final ManagedWorkspaceApplicationService service;
    private final boolean enabled;
    private final Duration interval;
    private volatile ScheduledExecutorService executor;
    private volatile boolean running;

    /**
     * 注入业务服务和补偿配置；默认开启，每 60 秒扫描一次当前服务器副本。
     */
    public ManagedWorkspaceReplicaReconciler(
            ManagedWorkspaceApplicationService service,
            @Value("${test-agent.managed-workspace.replica-reconciler.enabled:true}") boolean enabled,
            @Value("${test-agent.managed-workspace.replica-reconciler.interval-seconds:60}") long intervalSeconds) {
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
            Thread thread = new Thread(runnable, "managed-workspace-replica-reconciler");
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
            LOGGER.warn("Managed workspace replica reconciliation failed", exception);
        }
    }
}
