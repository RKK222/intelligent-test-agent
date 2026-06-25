package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.scheduler.ScheduledTask;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRun;
import java.time.Instant;
import java.util.Map;

/**
 * 定时任务管理 HTTP DTO，隔离入口字段命名和 scheduler 领域模型。
 */
final class SchedulerManagementDtos {

    private SchedulerManagementDtos() {
    }

    record UpdateTaskRequest(Boolean enabled, String cronExpression, Long lockTtlSeconds) {
    }

    record TaskResponse(
            String taskKey,
            String name,
            String cronExpression,
            boolean enabled,
            long lockTtlSeconds,
            Instant nextFireAt,
            String registrationStatus,
            Instant createdAt,
            Instant updatedAt,
            String traceId) {

        static TaskResponse from(ScheduledTask task) {
            return new TaskResponse(
                    task.taskKey().value(),
                    task.name(),
                    task.cronExpression(),
                    task.enabled(),
                    task.lockTtl().toSeconds(),
                    task.nextFireAt(),
                    task.registrationStatus().name(),
                    task.createdAt(),
                    task.updatedAt(),
                    task.traceId());
        }
    }

    record RunResponse(
            String taskRunId,
            String taskKey,
            String planId,
            String triggerType,
            String status,
            String requestedByUserId,
            Instant scheduledFireAt,
            Instant startedAt,
            Instant endedAt,
            String ownerInstanceId,
            String skipReason,
            String errorCode,
            String errorMessage,
            Map<String, Object> result,
            String traceId,
            Instant createdAt,
            Instant updatedAt) {

        static RunResponse from(ScheduledTaskRun run) {
            return new RunResponse(
                    run.taskRunId().value(),
                    run.taskKey().value(),
                    run.planId() == null ? null : run.planId().value(),
                    run.triggerType().name(),
                    run.status().name(),
                    run.requestedByUserId() == null ? null : run.requestedByUserId().value(),
                    run.scheduledFireAt(),
                    run.startedAt(),
                    run.endedAt(),
                    run.ownerInstanceId(),
                    run.skipReason(),
                    run.errorCode(),
                    run.errorMessage(),
                    run.result(),
                    run.traceId(),
                    run.createdAt(),
                    run.updatedAt());
        }
    }

    static PageResponse<TaskResponse> taskPage(PageResponse<ScheduledTask> page) {
        return new PageResponse<>(
                page.items().stream().map(TaskResponse::from).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    static PageResponse<RunResponse> runPage(PageResponse<ScheduledTaskRun> page) {
        return new PageResponse<>(
                page.items().stream().map(RunResponse::from).toList(),
                page.page(),
                page.size(),
                page.total());
    }
}
