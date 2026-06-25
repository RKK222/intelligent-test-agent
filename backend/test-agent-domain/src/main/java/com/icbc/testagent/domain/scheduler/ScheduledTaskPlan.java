package com.icbc.testagent.domain.scheduler;

import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 用户级 Cron 计划，首版仅作为后续“定时会话”能力的数据模型预留。
 */
public record ScheduledTaskPlan(
        ScheduledTaskPlanId planId,
        ScheduledTaskKey taskKey,
        UserId ownerUserId,
        String cronExpression,
        Map<String, Object> payload,
        boolean enabled,
        Instant nextFireAt,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 校验用户计划不变量，payload 固化为只读 Map 供后续后台会话发送使用。
     */
    public ScheduledTaskPlan {
        Objects.requireNonNull(planId, "planId must not be null");
        Objects.requireNonNull(taskKey, "taskKey must not be null");
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        cronExpression = DomainValidation.requireText(cronExpression, "cronExpression");
        payload = immutableCopy(payload);
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(value));
    }
}
