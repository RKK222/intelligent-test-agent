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
        Map<String, Object> payload) {

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
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(value));
    }
}
