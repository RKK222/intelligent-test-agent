package com.enterprise.testagent.xxljob;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 异步维护 Admin 子上下文。启动异常只更新独立健康状态，并按 5～60 秒指数退避重试。
 */
@Component
public class XxlJobAdminLifecycle implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(XxlJobAdminLifecycle.class);

    private final XxlJobProperties properties;
    private final XxlJobAdminState state;
    private final XxlJobAdminContextLauncher launcher;
    private final XxlJobAdminBridge bridge;
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "test-agent-xxl-admin-retry");
        thread.setDaemon(true);
        return thread;
    });

    private volatile ConfigurableApplicationContext childContext;
    private volatile boolean running;
    private volatile Duration retryDelay;
    private int consecutiveFailures;

    public XxlJobAdminLifecycle(
            XxlJobProperties properties,
            XxlJobAdminState state,
            XxlJobAdminContextLauncher launcher,
            XxlJobAdminBridge bridge) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.launcher = Objects.requireNonNull(launcher, "launcher must not be null");
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
        this.retryDelay = properties.getAdmin().getRetryInitialDelay();
    }

    @Override
    public void start() {
        if (!properties.isEnabled() || running) {
            return;
        }
        running = true;
        retryExecutor.execute(this::runAttempt);
    }

    private void runAttempt() {
        attemptStart();
        if (running && !state.isUp()) {
            retryExecutor.schedule(this::runAttempt, retryDelay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** 包级方法供故障重试单测直接驱动，绝不向主上下文抛出子上下文异常。 */
    synchronized void attemptStart() {
        if (!properties.isEnabled() || (childContext != null && childContext.isActive())) {
            return;
        }
        try {
            childContext = launcher.launch(bridge);
            state.up();
            retryDelay = properties.getAdmin().getRetryInitialDelay();
            consecutiveFailures = 0;
            LOGGER.info("XXL-JOB Admin 子上下文已启动，port={}", properties.getAdmin().getPort());
        } catch (RuntimeException exception) {
            state.down("XXL-JOB Admin 启动失败");
            retryDelay = consecutiveFailures == 0
                    ? properties.getAdmin().getRetryInitialDelay()
                    : min(retryDelay.multipliedBy(2), properties.getAdmin().getRetryMaxDelay());
            consecutiveFailures++;
            // 不记录第三方异常 message/堆栈，防止 JDBC URL、账号或密码进入平台日志。
            LOGGER.warn("XXL-JOB Admin 子上下文启动失败，errorType={}，将在 {} 秒后重试",
                    exception.getClass().getSimpleName(), retryDelay.toSeconds());
        }
    }

    Duration nextRetryDelay() {
        return retryDelay;
    }

    @Override
    public void stop() {
        running = false;
        retryExecutor.shutdownNow();
        ConfigurableApplicationContext context = childContext;
        childContext = null;
        if (context != null) {
            context.close();
        }
        state.down("XXL-JOB Admin 已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}
