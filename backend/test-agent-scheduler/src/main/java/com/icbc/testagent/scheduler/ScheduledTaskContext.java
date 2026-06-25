package com.icbc.testagent.scheduler;

import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.domain.scheduler.ScheduledTaskPlanId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * 传递给 handler 的单次运行上下文，避免业务任务直接读取框架内部运行记录。
 */
public record ScheduledTaskContext(
        ScheduledTaskRunId taskRunId,
        ScheduledTaskKey taskKey,
        ScheduledTaskPlanId planId,
        ScheduledTaskTriggerType triggerType,
        UserId requestedByUserId,
        Instant scheduledFireAt,
        String traceId,
        Map<String, Object> payload,
        BooleanSupplier stopRequestedSupplier) {

    /**
     * 创建不支持停止信号的上下文，兼容测试和简单 handler。
     */
    public ScheduledTaskContext(
            ScheduledTaskRunId taskRunId,
            ScheduledTaskKey taskKey,
            ScheduledTaskPlanId planId,
            ScheduledTaskTriggerType triggerType,
            UserId requestedByUserId,
            Instant scheduledFireAt,
            String traceId,
            Map<String, Object> payload) {
        this(taskRunId, taskKey, planId, triggerType, requestedByUserId, scheduledFireAt, traceId, payload, () -> false);
    }

    /**
     * 校验上下文关键字段并固化 payload，保证 handler 看到的是稳定快照。
     */
    public ScheduledTaskContext {
        Objects.requireNonNull(taskRunId, "taskRunId must not be null");
        Objects.requireNonNull(taskKey, "taskKey must not be null");
        Objects.requireNonNull(triggerType, "triggerType must not be null");
        Objects.requireNonNull(scheduledFireAt, "scheduledFireAt must not be null");
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        payload = immutableCopy(payload);
        stopRequestedSupplier = Objects.requireNonNull(stopRequestedSupplier, "stopRequestedSupplier must not be null");
    }

    /**
     * 返回管理员是否已经请求停止本次运行；长任务应在循环或外部调用间隙主动检查。
     */
    public boolean stopRequested() {
        return stopRequestedSupplier.getAsBoolean();
    }

    /**
     * 如果已收到停止请求则抛出协作式停止信号，runner 会统一写入人工停止终态。
     */
    public void throwIfStopRequested() {
        if (stopRequested()) {
            throw new ScheduledTaskStopRequestedException();
        }
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(value));
    }
}
