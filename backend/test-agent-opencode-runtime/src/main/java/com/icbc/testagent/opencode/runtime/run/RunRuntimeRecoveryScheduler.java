package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.observability.TraceIdSupport;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Java 启动后立即扫描，并每 5 秒先补偿终态 outbox、再按本服务器 active 索引尝试安全接管。 */
@Component
public class RunRuntimeRecoveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunRuntimeRecoveryScheduler.class);

    private final RunRuntimeRecoveryCoordinator coordinator;
    private final RunTerminalProjectionRecoveryCoordinator terminalProjectionCoordinator;
    private final RunApplicationService runApplicationService;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong lastLegacyReconcileAtMs = new AtomicLong();

    // 启动时立即扫描；进程尚未恢复时降低重试频率，避免每个旧 Run 持续打远端失败日志。
    private static final long LEGACY_RECONCILE_INTERVAL_MS = 30_000L;

    @Autowired
    public RunRuntimeRecoveryScheduler(
            RunRuntimeRecoveryCoordinator coordinator,
            RunTerminalProjectionRecoveryCoordinator terminalProjectionCoordinator,
            RunApplicationService runApplicationService) {
        this.coordinator = coordinator;
        this.terminalProjectionCoordinator = terminalProjectionCoordinator;
        this.runApplicationService = runApplicationService;
    }

    /** 测试兼容构造器；旧测试只关注 Redis/终态 outbox 顺序。 */
    public RunRuntimeRecoveryScheduler(
            RunRuntimeRecoveryCoordinator coordinator,
            RunTerminalProjectionRecoveryCoordinator terminalProjectionCoordinator) {
        this(coordinator, terminalProjectionCoordinator, null);
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
            reconcileLegacyRuns(traceId);
        } finally {
            running.set(false);
        }
    }

    private void reconcileLegacyRuns(String traceId) {
        if (runApplicationService == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastLegacyReconcileAtMs.get();
        if (previous != 0L && now - previous < LEGACY_RECONCILE_INTERVAL_MS) {
            return;
        }
        if (!lastLegacyReconcileAtMs.compareAndSet(previous, now)) {
            return;
        }
        try {
            int reconciled = runApplicationService.reconcileLegacyActiveRuns(traceId, () -> false);
            if (reconciled > 0) {
                LOGGER.info("重启后 legacy active Run 终态补偿完成，reconciledCount={}, traceId={}", reconciled, traceId);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "legacy active Run 终态补偿扫描失败，等待下轮重试，traceId={}, exceptionType={}",
                    traceId,
                    exception.getClass().getSimpleName());
        }
    }
}
