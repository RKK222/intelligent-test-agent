package com.enterprise.testagent.xxljob;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
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
import java.time.Instant;
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

    /** 解析 XXL 参数并执行已注册任务；traceId 由统一 XXL 入口生成并贯穿业务 handler 与详情日志。 */
    public XxlJobTaskExecutionOutcome execute(
            String rawParameter,
            String traceId,
            ExecutionLog executionLog) {
        Objects.requireNonNull(executionLog, "executionLog must not be null");
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
        ExecutionMetadata metadata = new ExecutionMetadata(
                taskKey,
                handler.name(),
                new ScheduledTaskRunId(RuntimeIdGenerator.scheduledTaskRunId()),
                requireText(traceId, "traceId"),
                policy,
                clock.instant());
        executionLog.write(
                "【任务信息】名称={}，taskKey={}，taskRunId={}，traceId={}，并发策略={}，触发类型=CRON，计划触发时间={}",
                metadata.taskName(),
                taskKey.value(),
                metadata.taskRunId().value(),
                metadata.traceId(),
                policy.name(),
                metadata.startedAt());
        try {
            if (policy == XxlJobConcurrencyPolicy.ALLOW_OVERLAP) {
                executionLog.write("【并发控制】允许重叠执行，本轮不申请 Redis 全局锁");
                return invoke(handler, metadata, parameter.payload(), new AtomicBoolean(false), executionLog);
            }
            Optional<ScheduledTaskLockLease> acquired = lock.acquire(taskKey, handler.lockTtl());
            if (acquired.isEmpty()) {
                executionLog.write("【并发控制】未取得 Redis 全局锁，本轮不执行业务处理");
                return XxlJobTaskExecutionOutcome.skippedLockHeld(
                        taskKey.value(),
                        metadata.taskName(),
                        metadata.taskRunId().value(),
                        metadata.traceId(),
                        policy,
                        metadata.startedAt(),
                        clock.instant());
            }
            ScheduledTaskLockLease lease = acquired.get();
            AtomicBoolean renewalLost = new AtomicBoolean(false);
            ScheduledFuture<?> renewal = scheduleRenewal(lease, renewalLost);
            executionLog.write("【并发控制】已取得 Redis 全局锁，锁租期={}ms", lease.ttl().toMillis());
            try {
                XxlJobTaskExecutionOutcome outcome =
                        invoke(handler, metadata, parameter.payload(), renewalLost, executionLog);
                if (renewalLost.get()) {
                    throw new PlatformException(ErrorCode.CONFLICT, "定时任务 Redis 锁续租失败");
                }
                return outcome;
            } finally {
                renewal.cancel(true);
                lease.release();
                executionLog.write("【并发控制】Redis 全局锁已释放");
            }
        } catch (PlatformException exception) {
            logFailure(metadata, exception.errorCode().name(), exception.getMessage(), executionLog);
            throw exception;
        } catch (RuntimeException exception) {
            logFailure(metadata, ErrorCode.INTERNAL_ERROR.name(), "定时任务执行失败", executionLog);
            throw exception;
        }
    }

    private XxlJobTaskExecutionOutcome invoke(
            ScheduledTaskHandler handler,
            ExecutionMetadata metadata,
            Map<String, Object> payload,
            AtomicBoolean renewalLost,
            ExecutionLog executionLog) {
        Thread executionThread = Thread.currentThread();
        ScheduledTaskContext context = new ScheduledTaskContext(
                metadata.taskRunId(),
                metadata.taskKey(),
                null,
                ScheduledTaskTriggerType.CRON,
                null,
                metadata.startedAt(),
                metadata.traceId(),
                payload,
                () -> executionThread.isInterrupted() || renewalLost.get());
        executionLog.write("【业务处理】开始调用已注册 handler");
        try {
            ScheduledTaskResult result = handler.run(context);
            return new XxlJobTaskExecutionOutcome(
                    XxlJobTaskExecutionStatus.SUCCEEDED,
                    metadata.taskKey().value(),
                    metadata.taskName(),
                    metadata.taskRunId().value(),
                    metadata.traceId(),
                    metadata.concurrencyPolicy(),
                    metadata.startedAt(),
                    clock.instant(),
                    result == null ? Map.of() : result.result());
        } catch (PlatformException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 第三方异常 message 可能含参数或凭据，对 XXL 只暴露稳定安全错误。
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "定时任务执行失败", Map.of(), exception);
        }
    }

    /** 失败详情只记录稳定任务标识与安全错误，不输出原始参数或第三方异常 message。 */
    private void logFailure(
            ExecutionMetadata metadata,
            String errorCode,
            String safeMessage,
            ExecutionLog executionLog) {
        long durationMillis = Math.max(
                0L,
                Duration.between(metadata.startedAt(), clock.instant()).toMillis());
        executionLog.write(
                "【任务失败】名称={}，taskKey={}，taskRunId={}，traceId={}，errorCode={}，message={}，durationMillis={}",
                metadata.taskName(),
                metadata.taskKey().value(),
                metadata.taskRunId().value(),
                metadata.traceId(),
                errorCode,
                safeMessage,
                durationMillis);
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

    private record ExecutionMetadata(
            ScheduledTaskKey taskKey,
            String taskName,
            ScheduledTaskRunId taskRunId,
            String traceId,
            XxlJobConcurrencyPolicy concurrencyPolicy,
            Instant startedAt) {
    }

    /** 由统一入口提供的 XXL 日志写入回调，避免适配器在停止线程上初始化静态日志上下文。 */
    @FunctionalInterface
    public interface ExecutionLog {
        void write(String template, Object... arguments);
    }
}
