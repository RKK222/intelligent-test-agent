package com.enterprise.testagent.domain.scheduler;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 代码注册的定时任务定义，数据库可保存管理员启停和 cron 覆盖值。
 */
public record ScheduledTask(
        ScheduledTaskKey taskKey,
        String name,
        String cronExpression,
        boolean enabled,
        Duration lockTtl,
        Instant nextFireAt,
        ScheduledTaskRegistrationStatus registrationStatus,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 构造已注册任务定义，默认启用并等待调度服务计算下次触发时间。
     */
    public static ScheduledTask registered(
            ScheduledTaskKey taskKey,
            String name,
            String cronExpression,
            Duration lockTtl,
            Instant now,
            String traceId) {
        return new ScheduledTask(
                taskKey,
                name,
                cronExpression,
                true,
                lockTtl,
                null,
                ScheduledTaskRegistrationStatus.REGISTERED,
                now,
                now,
                traceId);
    }

    /**
     * 校验任务定义必填字段和锁 TTL 边界。
     */
    public ScheduledTask {
        Objects.requireNonNull(taskKey, "taskKey must not be null");
        name = DomainValidation.requireText(name, "name");
        cronExpression = DomainValidation.requireText(cronExpression, "cronExpression");
        lockTtl = Objects.requireNonNull(lockTtl, "lockTtl must not be null");
        if (lockTtl.isZero() || lockTtl.isNegative()) {
            throw new IllegalArgumentException("lockTtl must be positive");
        }
        Objects.requireNonNull(registrationStatus, "registrationStatus must not be null");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    /**
     * 应用管理员覆盖的启停、cron 和锁租约配置。
     */
    public ScheduledTask withAdminSchedule(boolean enabled, String cronExpression, Duration lockTtl, Instant updatedAt) {
        return new ScheduledTask(
                taskKey,
                name,
                cronExpression,
                enabled,
                lockTtl,
                nextFireAt,
                registrationStatus,
                createdAt,
                updatedAt,
                traceId);
    }

    /**
     * 更新下次触发时间。
     */
    public ScheduledTask withNextFireAt(Instant nextFireAt, Instant updatedAt) {
        return new ScheduledTask(
                taskKey,
                name,
                cronExpression,
                enabled,
                lockTtl,
                nextFireAt,
                registrationStatus,
                createdAt,
                updatedAt,
                traceId);
    }
}
