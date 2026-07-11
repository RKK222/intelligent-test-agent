package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.observability.TraceIdSupport;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Java 启动后立即扫描，并每 5 秒先补偿终态 outbox、再按本服务器 active 索引尝试安全接管。 */
@Component
public class RunRuntimeRecoveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunRuntimeRecoveryScheduler.class);

    private final RunRuntimeRecoveryCoordinator coordinator;
    private final RunTerminalProjectionRecoveryCoordinator terminalProjectionCoordinator;
    private final AtomicBoolean running = new AtomicBoolean();

    public RunRuntimeRecoveryScheduler(
            RunRuntimeRecoveryCoordinator coordinator,
            RunTerminalProjectionRecoveryCoordinator terminalProjectionCoordinator) {
        this.coordinator = coordinator;
        this.terminalProjectionCoordinator = terminalProjectionCoordinator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recover();
    }

    @Scheduled(fixedDelay = 5_000L, scheduler = RunRuntimeSchedulingConfiguration.MAINTENANCE_SCHEDULER)
    public void recoverPeriodically() {
        recover();
    }

    private void recover() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        String traceId = TraceIdSupport.generate();
        try {
            try {
                terminalProjectionCoordinator.recoverCurrentServer(traceId, () -> false);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Run 终态 outbox 恢复扫描失败，等待下轮重试，traceId={}, exceptionType={}",
                        traceId,
                        exception.getClass().getSimpleName());
            }
            try {
                coordinator.recoverCurrentServer(traceId, () -> false);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Redis Run 恢复扫描失败，等待下轮重试，traceId={}, exceptionType={}",
                        traceId,
                        exception.getClass().getSimpleName());
            }
        } finally {
            running.set(false);
        }
    }
}
