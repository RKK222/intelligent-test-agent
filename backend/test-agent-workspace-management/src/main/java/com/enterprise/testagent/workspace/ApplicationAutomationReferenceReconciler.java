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

/** 自动化引用共享副本补偿器，恢复广播丢失、Java 重启和离线服务器回归。 */
@Component
public class ApplicationAutomationReferenceReconciler implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationAutomationReferenceReconciler.class);
    private final ApplicationAutomationReferenceService service;
    private final boolean enabled;
    private final Duration interval;
    private volatile ScheduledExecutorService executor;
    private volatile boolean running;

    public ApplicationAutomationReferenceReconciler(
            ApplicationAutomationReferenceService service,
            @Value("${test-agent.automation-reference.replica-reconciler.enabled:true}") boolean enabled,
            @Value("${test-agent.automation-reference.replica-reconciler.interval-seconds:60}") long intervalSeconds) {
        this.service = Objects.requireNonNull(service);
        this.enabled = enabled;
        this.interval = Duration.ofSeconds(Math.max(10L, intervalSeconds));
    }

    @Override
    public void start() {
        if (running || !enabled) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "automation-reference-replica-reconciler");
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
            LOGGER.warn("Automation reference replica reconciliation failed");
        }
    }
}
