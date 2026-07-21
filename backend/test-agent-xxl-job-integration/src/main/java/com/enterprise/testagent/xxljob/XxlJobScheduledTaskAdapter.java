package com.enterprise.testagent.xxljob;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.observability.TraceIdSupport;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskLock;
import com.enterprise.testagent.scheduler.ScheduledTaskLockLease;
import com.enterprise.testagent.scheduler.ScheduledTaskRegistry;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 把 XXL 通用 handler 参数转换为现有 ScheduledTaskHandler 调用，并复用同一 Redis 锁键。 */
@Component
public class XxlJobScheduledTaskAdapter {

    private final ScheduledTaskRegistry registry;
    private final ScheduledTaskLock lock;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ScheduledExecutorService renewalExecutor;
    private final Duration renewalMinimum;
    private final boolean ownsExecutor;

    @Autowired
    public XxlJobScheduledTaskAdapter(
            ScheduledTaskRegistry registry,
            ScheduledTaskLock lock,
            ObjectMapper objectMapper,
            Clock clock) {
        this(
                registry,
                lock,
                objectMapper,
                clock,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "test-agent-xxl-lock-renewal");
                    thread.setDaemon(true);
                    return thread;
                }),
                Duration.ofSeconds(1),
                true);
    }

    XxlJobScheduledTaskAdapter(
            ScheduledTaskRegistry registry,
            ScheduledTaskLock lock,
            ObjectMapper objectMapper,
            Clock clock,
            ScheduledExecutorService renewalExecutor,
            Duration renewalMinimum) {
        this(registry, lock, objectMapper, clock, renewalExecutor, renewalMinimum, false);
    }

    private XxlJobScheduledTaskAdapter(
            ScheduledTaskRegistry registry,
            ScheduledTaskLock lock,
            ObjectMapper objectMapper,
            Clock clock,
            ScheduledExecutorService renewalExecutor,
            Duration renewalMinimum,
            boolean ownsExecutor) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.lock = Objects.requireNonNull(lock, "lock must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.renewalExecutor = Objects.requireNonNull(renewalExecutor, "renewalExecutor must not be null");
        this.renewalMinimum = positive(renewalMinimum);
        this.ownsExecutor = ownsExecutor;
    }

    public XxlJobTaskExecutionOutcome execute(String rawParameter) {
        TaskParameter parameter = parse(rawParameter);
        ScheduledTaskKey taskKey = parseTaskKey(parameter.taskKey());
        ScheduledTaskHandler handler = registry.handlerFor(taskKey)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "定时任务 handler 未注册",
                        Map.of("taskKey", taskKey.value())));
        if (!handler.supportedTriggerTypes().contains(ScheduledTaskTriggerType.CRON)) {
            throw new PlatformException(ErrorCode.CONFLICT, "定时任务 handler 不接受周期触发");
        }
        XxlJobConcurrencyPolicy policy = parsePolicy(parameter.concurrencyPolicy());
        if (policy == XxlJobConcurrencyPolicy.ALLOW_OVERLAP) {
            return invoke(handler, taskKey, parameter.payload(), new AtomicBoolean(false));
        }
        Optional<ScheduledTaskLockLease> acquired = lock.acquire(taskKey, handler.lockTtl());
        if (acquired.isEmpty()) {
            return XxlJobTaskExecutionOutcome.skippedLockHeld();
        }
        ScheduledTaskLockLease lease = acquired.get();
        AtomicBoolean renewalLost = new AtomicBoolean(false);
        ScheduledFuture<?> renewal = scheduleRenewal(lease, renewalLost);
        try {
            XxlJobTaskExecutionOutcome outcome = invoke(handler, taskKey, parameter.payload(), renewalLost);
            if (renewalLost.get()) {
                throw new PlatformException(ErrorCode.CONFLICT, "定时任务 Redis 锁续租失败");
            }
            return outcome;
        } finally {
            renewal.cancel(true);
            lease.release();
        }
    }

    private XxlJobTaskExecutionOutcome invoke(
            ScheduledTaskHandler handler,
            ScheduledTaskKey taskKey,
            Map<String, Object> payload,
            AtomicBoolean renewalLost) {
        Thread executionThread = Thread.currentThread();
        ScheduledTaskContext context = new ScheduledTaskContext(
                new ScheduledTaskRunId(RuntimeIdGenerator.scheduledTaskRunId()),
                taskKey,
                null,
                ScheduledTaskTriggerType.CRON,
                null,
                clock.instant(),
                TraceIdSupport.generate(),
                payload,
                () -> executionThread.isInterrupted() || renewalLost.get());
        try {
            ScheduledTaskResult result = handler.run(context);
            return new XxlJobTaskExecutionOutcome(
                    XxlJobTaskExecutionStatus.SUCCEEDED,
                    result == null ? Map.of() : result.result());
        } catch (PlatformException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 第三方异常 message 可能含参数或凭据，对 XXL 只暴露稳定安全错误。
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "定时任务执行失败", Map.of(), exception);
        }
    }

    private ScheduledFuture<?> scheduleRenewal(ScheduledTaskLockLease lease, AtomicBoolean renewalLost) {
        long periodMillis = Math.max(renewalMinimum.toMillis(), Math.max(1L, lease.ttl().toMillis() / 3));
        return renewalExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        if (!lease.renew()) {
                            renewalLost.set(true);
                        }
                    } catch (RuntimeException exception) {
                        renewalLost.set(true);
                    }
                },
                periodMillis,
                periodMillis,
                TimeUnit.MILLISECONDS);
    }

    private TaskParameter parse(String rawParameter) {
        if (rawParameter == null || rawParameter.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "XXL-JOB 任务参数不能为空");
        }
        try {
            TaskParameter parameter = objectMapper.readValue(rawParameter, TaskParameter.class);
            return new TaskParameter(
                    requireText(parameter.taskKey(), "taskKey"),
                    requireText(parameter.concurrencyPolicy(), "concurrencyPolicy"),
                    parameter.payload());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "XXL-JOB 任务参数格式无效");
        }
    }

    private ScheduledTaskKey parseTaskKey(String value) {
        try {
            return new ScheduledTaskKey(value);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "XXL-JOB taskKey 无效");
        }
    }

    private XxlJobConcurrencyPolicy parsePolicy(String value) {
        try {
            return XxlJobConcurrencyPolicy.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "XXL-JOB 并发策略无效",
                    Map.of("allowed", java.util.List.of("GLOBAL_MUTEX", "ALLOW_OVERLAP")));
        }
    }

    @PreDestroy
    void close() {
        if (ownsExecutor) {
            renewalExecutor.shutdownNow();
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static Duration positive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("renewalMinimum must be positive");
        }
        return value;
    }

    private record TaskParameter(String taskKey, String concurrencyPolicy, Map<String, Object> payload) {
        private TaskParameter {
            payload = payload == null || payload.isEmpty()
                    ? Map.of()
                    : Map.copyOf(new LinkedHashMap<>(payload));
        }
    }
}
