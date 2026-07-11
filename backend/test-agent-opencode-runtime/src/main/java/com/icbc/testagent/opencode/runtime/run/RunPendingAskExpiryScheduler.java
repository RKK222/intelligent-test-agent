package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.observability.TraceIdSupport;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Java 启动完成后立即扫描，并每 5 秒触发一次本服务器超期待处理 Run 收敛。 */
@Component
public class RunPendingAskExpiryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunPendingAskExpiryScheduler.class);

    private final RunPendingAskExpiryCoordinator coordinator;
    private final AtomicBoolean running = new AtomicBoolean();

    public RunPendingAskExpiryScheduler(RunPendingAskExpiryCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /** 启动触发不等待首个周期，避免重启后额外延长 7 天到期窗口。 */
    @EventListener(ApplicationReadyEvent.class)
    public void expireOnStartup() {
        expire();
    }

    /** 每个 Java 本地触发，真正的单节点执行由公共路由选择和 owner lease 保证。 */
    @Scheduled(fixedDelay = 5_000L, scheduler = RunRuntimeSchedulingConfiguration.MAINTENANCE_SCHEDULER)
    public void expirePeriodically() {
        expire();
    }

    private void expire() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        String traceId = TraceIdSupport.generate();
        try {
            coordinator.expireCurrentServer(traceId, () -> false);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "超期待处理 Run 扫描失败，等待下轮重试，traceId={}, exceptionType={}",
                    traceId,
                    exception.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }
}
