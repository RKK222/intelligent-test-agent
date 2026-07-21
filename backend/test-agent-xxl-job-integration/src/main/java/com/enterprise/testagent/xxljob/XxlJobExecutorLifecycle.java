package com.enterprise.testagent.xxljob;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * 异步等待本机 XXL Admin readiness 就绪后再启动 executor，消除同 JVM Admin/executor 的启动注册竞态。
 * 等待过程不阻塞平台 WebFlux 启动，也不改变平台 readiness 判定。
 */
class XxlJobExecutorLifecycle implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(XxlJobExecutorLifecycle.class);
    private static final Duration RETRY_INITIAL_DELAY = Duration.ofMillis(250);
    private static final Duration RETRY_MAX_DELAY = Duration.ofSeconds(5);
    private static final int PHASE_AFTER_ADMIN_LIFECYCLE = 100;

    private final XxlJobProperties properties;
    private final XxlJobEndpointResolver.Endpoints endpoints;
    private final XxlJobAdminReadinessProbe readinessProbe;
    private final DeferredXxlJobSpringExecutor executor;
    private final ScheduledExecutorService retryExecutor;
    private final Object lifecycleMonitor = new Object();

    private volatile boolean running;
    private volatile boolean executorStarted;
    private volatile boolean terminalStartFailure;
    private Duration retryDelay = RETRY_INITIAL_DELAY;

    XxlJobExecutorLifecycle(
            XxlJobProperties properties,
            XxlJobEndpointResolver.Endpoints endpoints,
            XxlJobAdminReadinessProbe readinessProbe,
            DeferredXxlJobSpringExecutor executor) {
        this(properties, endpoints, readinessProbe, executor, newRetryExecutor());
    }

    /** 包级调度器注入只用于确定性单测，生产始终使用独立 daemon 线程。 */
    XxlJobExecutorLifecycle(
            XxlJobProperties properties,
            XxlJobEndpointResolver.Endpoints endpoints,
            XxlJobAdminReadinessProbe readinessProbe,
            DeferredXxlJobSpringExecutor executor,
            ScheduledExecutorService retryExecutor) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints must not be null");
        this.readinessProbe = Objects.requireNonNull(readinessProbe, "readinessProbe must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.retryExecutor = Objects.requireNonNull(retryExecutor, "retryExecutor must not be null");
    }

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (!properties.isEnabled() || running) {
                return;
            }
            running = true;
        }
        LOGGER.info("XXL-JOB executor 等待本机 Admin readiness 就绪后启动");
        try {
            retryExecutor.execute(this::runAttempt);
        } catch (RejectedExecutionException exception) {
            running = false;
            LOGGER.warn("XXL-JOB executor readiness 调度器不可用，errorType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void runAttempt() {
        if (!running || executorStarted || terminalStartFailure) {
            return;
        }
        if (readinessProbe.isReady(endpoints.adminAddress())) {
            startExecutor();
            return;
        }
        scheduleRetry();
    }

    private void startExecutor() {
        synchronized (lifecycleMonitor) {
            if (!running || executorStarted || terminalStartFailure) {
                return;
            }
            try {
                executor.startAfterAdminReady();
                executorStarted = true;
                LOGGER.info("XXL-JOB executor 已在 Admin readiness 就绪后启动，appName={}，port={}",
                        properties.getExecutor().getAppName(), properties.getExecutor().getPort());
            } catch (RuntimeException exception) {
                terminalStartFailure = true;
                // 配置和第三方异常 message 可能含地址等运行信息，只记录稳定类型和非敏感端口。
                LOGGER.error("XXL-JOB executor 启动失败，errorType={}，port={}",
                        exception.getClass().getSimpleName(), properties.getExecutor().getPort());
            }
        }
    }

    private void scheduleRetry() {
        Duration delay;
        synchronized (lifecycleMonitor) {
            if (!running || executorStarted || terminalStartFailure) {
                return;
            }
            delay = retryDelay;
            retryDelay = min(retryDelay.multipliedBy(2), RETRY_MAX_DELAY);
        }
        try {
            retryExecutor.schedule(this::runAttempt, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException exception) {
            if (running) {
                LOGGER.warn("XXL-JOB executor readiness 重试调度失败，errorType={}",
                        exception.getClass().getSimpleName());
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        retryExecutor.shutdownNow();
        synchronized (lifecycleMonitor) {
            if (executorStarted) {
                executor.destroy();
                executorStarted = false;
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    boolean isExecutorStarted() {
        return executorStarted;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return PHASE_AFTER_ADMIN_LIFECYCLE;
    }

    private static ScheduledExecutorService newRetryExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "test-agent-xxl-executor-readiness");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}
