package com.enterprise.testagent.domain.scheduler;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 定时任务单次运行记录，包含 cron、手动触发和用户计划触发的统一审计字段。
 */
public record ScheduledTaskRun(
        ScheduledTaskRunId taskRunId,
        ScheduledTaskKey taskKey,
        ScheduledTaskPlanId planId,
        ScheduledTaskTriggerType triggerType,
        ScheduledTaskRunStatus status,
        UserId requestedByUserId,
        Instant scheduledFireAt,
        Instant startedAt,
        Instant endedAt,
        String ownerInstanceId,
        Instant stopRequestedAt,
        UserId stopRequestedByUserId,
        String stopReason,
        String skipReason,
        String errorCode,
        String errorMessage,
        Map<String, Object> result,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        String executionAffinity) {

    /** 兼容新增执行亲和字段前的构造调用。 */
    public ScheduledTaskRun(
            ScheduledTaskRunId taskRunId,
            ScheduledTaskKey taskKey,
            ScheduledTaskPlanId planId,
            ScheduledTaskTriggerType triggerType,
            ScheduledTaskRunStatus status,
            UserId requestedByUserId,
            Instant scheduledFireAt,
            Instant startedAt,
            Instant endedAt,
            String ownerInstanceId,
            Instant stopRequestedAt,
            UserId stopRequestedByUserId,
            String stopReason,
            String skipReason,
            String errorCode,
            String errorMessage,
            Map<String, Object> result,
            String traceId,
            Instant createdAt,
            Instant updatedAt) {
        this(taskRunId, taskKey, planId, triggerType, status, requestedByUserId, scheduledFireAt,
                startedAt, endedAt, ownerInstanceId, stopRequestedAt, stopRequestedByUserId,
                stopReason, skipReason, errorCode, errorMessage, result, traceId, createdAt, updatedAt, null);
    }

    /**
     * 创建待执行运行记录，等待 runner 抢锁并调用 handler。
     */
    public static ScheduledTaskRun pending(
            ScheduledTaskRunId taskRunId,
            ScheduledTaskKey taskKey,
            ScheduledTaskPlanId planId,
            ScheduledTaskTriggerType triggerType,
            UserId requestedByUserId,
            Instant scheduledFireAt,
            String traceId) {
        return pending(taskRunId, taskKey, planId, triggerType, requestedByUserId, scheduledFireAt, null, traceId);
    }

    /** 创建带目标服务器亲和键的用户计划运行。 */
    public static ScheduledTaskRun pending(
            ScheduledTaskRunId taskRunId,
            ScheduledTaskKey taskKey,
            ScheduledTaskPlanId planId,
            ScheduledTaskTriggerType triggerType,
            UserId requestedByUserId,
            Instant scheduledFireAt,
            String executionAffinity,
            String traceId) {
        return pending(taskRunId, taskKey, planId, triggerType, requestedByUserId, scheduledFireAt,
                executionAffinity, traceId, scheduledFireAt);
    }

    /** 创建可提前入库、在未来触发的运行记录。 */
    public static ScheduledTaskRun pending(
            ScheduledTaskRunId taskRunId,
            ScheduledTaskKey taskKey,
            ScheduledTaskPlanId planId,
            ScheduledTaskTriggerType triggerType,
            UserId requestedByUserId,
            Instant scheduledFireAt,
            String executionAffinity,
            String traceId,
            Instant createdAt) {
        return new ScheduledTaskRun(
                taskRunId,
                taskKey,
                planId,
                triggerType,
                ScheduledTaskRunStatus.PENDING,
                requestedByUserId,
                scheduledFireAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                traceId,
                createdAt,
                createdAt,
                executionAffinity);
    }

    /**
     * 校验运行记录不变量并固化结果 Map，避免调用方修改审计快照。
     */
    public ScheduledTaskRun {
        Objects.requireNonNull(taskRunId, "taskRunId must not be null");
        Objects.requireNonNull(taskKey, "taskKey must not be null");
        Objects.requireNonNull(triggerType, "triggerType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        scheduledFireAt = DomainValidation.requireInstant(scheduledFireAt, "scheduledFireAt");
        if (startedAt != null && startedAt.isBefore(scheduledFireAt)) {
            throw new IllegalArgumentException("startedAt must not be before scheduledFireAt");
        }
        if (endedAt != null && startedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must not be before startedAt");
        }
        if ((stopRequestedAt == null) != (stopRequestedByUserId == null)) {
            throw new IllegalArgumentException("stopRequestedAt and stopRequestedByUserId must appear together");
        }
        if (stopRequestedAt != null && startedAt != null && stopRequestedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("stopRequestedAt must not be before startedAt");
        }
        if (endedAt != null && stopRequestedAt != null && endedAt.isBefore(stopRequestedAt)) {
            throw new IllegalArgumentException("endedAt must not be before stopRequestedAt");
        }
        ownerInstanceId = optionalText(ownerInstanceId);
        stopReason = optionalText(stopReason);
        skipReason = optionalText(skipReason);
        errorCode = optionalText(errorCode);
        errorMessage = optionalText(errorMessage);
        executionAffinity = optionalText(executionAffinity);
        result = immutableCopy(result);
        traceId = DomainValidation.requireText(traceId, "traceId");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    /**
     * 标记任务开始执行。
     */
    public ScheduledTaskRun start(String ownerInstanceId, Instant startedAt) {
        return new ScheduledTaskRun(
                taskRunId,
                taskKey,
                planId,
                triggerType,
                ScheduledTaskRunStatus.RUNNING,
                requestedByUserId,
                scheduledFireAt,
                startedAt,
                null,
                ownerInstanceId,
                null,
                null,
                null,
                null,
                null,
                null,
                result,
                traceId,
                createdAt,
                startedAt,
                executionAffinity);
    }

    /**
     * 标记任务因互斥或禁用等原因跳过。
     */
    public ScheduledTaskRun skip(String skipReason, Instant endedAt) {
        return new ScheduledTaskRun(
                taskRunId,
                taskKey,
                planId,
                triggerType,
                ScheduledTaskRunStatus.SKIPPED,
                requestedByUserId,
                scheduledFireAt,
                startedAt,
                endedAt,
                ownerInstanceId,
                stopRequestedAt,
                stopRequestedByUserId,
                stopReason,
                skipReason,
                null,
                null,
                result,
                traceId,
                createdAt,
                endedAt,
                executionAffinity);
    }

    /**
     * 标记任务成功并保存 handler 返回的结构化结果。
     */
    public ScheduledTaskRun succeed(Map<String, Object> result, Instant endedAt) {
        return finish(ScheduledTaskRunStatus.SUCCEEDED, null, null, result, endedAt);
    }

    /**
     * 标记任务失败并保存安全错误摘要。
     */
    public ScheduledTaskRun fail(String errorCode, String errorMessage, Instant endedAt) {
        return finish(ScheduledTaskRunStatus.FAILED, errorCode, errorMessage, result, endedAt);
    }

    /**
     * 标记管理员已请求停止；业务 handler 需要通过上下文协作式退出。
     */
    public ScheduledTaskRun requestStop(UserId requestedByUserId, String stopReason, Instant requestedAt) {
        Objects.requireNonNull(requestedByUserId, "requestedByUserId must not be null");
        return new ScheduledTaskRun(
                taskRunId,
                taskKey,
                planId,
                triggerType,
                ScheduledTaskRunStatus.STOPPING,
                requestedByUserId(),
                scheduledFireAt,
                startedAt,
                null,
                ownerInstanceId,
                requestedAt,
                requestedByUserId,
                stopReason,
                skipReason,
                errorCode,
                errorMessage,
                result,
                traceId,
                createdAt,
                requestedAt,
                executionAffinity);
    }

    /**
     * handler 协作式退出后写入最终人工停止状态。
     */
    public ScheduledTaskRun manuallyStopped(Instant endedAt) {
        return finish(ScheduledTaskRunStatus.MANUALLY_STOPPED, null, null, result, endedAt);
    }

    private ScheduledTaskRun finish(
            ScheduledTaskRunStatus status,
            String errorCode,
            String errorMessage,
            Map<String, Object> result,
            Instant endedAt) {
        return new ScheduledTaskRun(
                taskRunId,
                taskKey,
                planId,
                triggerType,
                status,
                requestedByUserId,
                scheduledFireAt,
                startedAt,
                endedAt,
                ownerInstanceId,
                stopRequestedAt,
                stopRequestedByUserId,
                stopReason,
                skipReason,
                errorCode,
                errorMessage,
                result,
                traceId,
                createdAt,
                endedAt,
                executionAffinity);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(value));
    }
}
