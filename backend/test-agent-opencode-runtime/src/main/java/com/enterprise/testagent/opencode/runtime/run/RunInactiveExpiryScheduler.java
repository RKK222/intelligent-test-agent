package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.observability.TraceIdSupport;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 启动时及每 30 秒检查普通 Redis Run 的两小时无活动截止。 */
@Component
public class RunInactiveExpiryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunInactiveExpiryScheduler.class);

    private final RunInactiveExpiryCoordinator coordinator;
    private final AtomicBoolean running = new AtomicBoolean();

    public RunInactiveExpiryScheduler(RunInactiveExpiryCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void expireOnStartup() {
        expire();
    }

    @Scheduled(fixedDelay = 30_000L, scheduler = RunRuntimeSchedulingConfiguration.MAINTENANCE_SCHEDULER)
    public void expirePeriodically() {
        expire();
    }

    private void expire() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        String traceId = TraceIdSupport.generate();
        try {
            coordinator.expireCurrentServer(traceId);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Inactive Redis Run scan deferred, errorType={}, traceId={}",
                    exception.getClass().getSimpleName(),
                    traceId);
        } finally {
            running.set(false);
        }
    }
}
