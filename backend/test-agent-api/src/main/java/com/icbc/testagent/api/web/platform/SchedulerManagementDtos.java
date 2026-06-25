package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.scheduler.ScheduledTask;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRun;
import com.icbc.testagent.scheduler.SchedulerManagementService;
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
            String registrationStatusLabel,
            RunSummaryResponse currentRun,
            RunSummaryResponse latestRun,
            Instant createdAt,
            Instant updatedAt,
            String traceId) {

        static TaskResponse from(ScheduledTask task, SchedulerManagementService service) {
            return new TaskResponse(
                    task.taskKey().value(),
                    task.name(),
                    task.cronExpression(),
                    task.enabled(),
                    task.lockTtl().toSeconds(),
                    task.nextFireAt(),
                    task.registrationStatus().name(),
                    service.registrationStatusLabel(task.registrationStatus()),
                    service.findCurrentRunByTaskKey(task.taskKey())
                            .map(run -> RunSummaryResponse.from(run, service))
                            .orElse(null),
                    service.findLatestRunByTaskKey(task.taskKey())
                            .map(run -> RunSummaryResponse.from(run, service))
                            .orElse(null),
                    task.createdAt(),
                    task.updatedAt(),
                    task.traceId());
        }
    }

    record RunSummaryResponse(
            String taskRunId,
            String status,
            String statusLabel,
            String triggerType,
            String triggerTypeLabel,
            String requestedByUserId,
            Instant scheduledFireAt,
            Instant startedAt,
            Instant endedAt,
            String ownerInstanceId) {

        static RunSummaryResponse from(ScheduledTaskRun run, SchedulerManagementService service) {
            return new RunSummaryResponse(
                    run.taskRunId().value(),
                    run.status().name(),
                    service.runStatusLabel(run.status()),
                    run.triggerType().name(),
                    service.triggerTypeLabel(run.triggerType()),
                    run.requestedByUserId() == null ? null : run.requestedByUserId().value(),
                    run.scheduledFireAt(),
                    run.startedAt(),
                    run.endedAt(),
                    run.ownerInstanceId());
        }
    }

    record RunResponse(
            String taskRunId,
            String taskKey,
            String planId,
            String triggerType,
            String triggerTypeLabel,
            String status,
            String statusLabel,
            String requestedByUserId,
            Instant scheduledFireAt,
            Instant startedAt,
            Instant endedAt,
            String ownerInstanceId,
            Instant stopRequestedAt,
            String stopRequestedByUserId,
            String stopReason,
            String skipReason,
            String errorCode,
            String errorMessage,
            Map<String, Object> result,
            String traceId,
            Instant createdAt,
            Instant updatedAt) {

        static RunResponse from(ScheduledTaskRun run, SchedulerManagementService service) {
            return new RunResponse(
                    run.taskRunId().value(),
                    run.taskKey().value(),
                    run.planId() == null ? null : run.planId().value(),
                    run.triggerType().name(),
                    service.triggerTypeLabel(run.triggerType()),
                    run.status().name(),
                    service.runStatusLabel(run.status()),
                    run.requestedByUserId() == null ? null : run.requestedByUserId().value(),
                    run.scheduledFireAt(),
                    run.startedAt(),
                    run.endedAt(),
                    run.ownerInstanceId(),
                    run.stopRequestedAt(),
                    run.stopRequestedByUserId() == null ? null : run.stopRequestedByUserId().value(),
                    run.stopReason(),
                    run.skipReason(),
                    run.errorCode(),
                    run.errorMessage(),
                    run.result(),
                    run.traceId(),
                    run.createdAt(),
                    run.updatedAt());
        }
    }

    static PageResponse<TaskResponse> taskPage(PageResponse<ScheduledTask> page, SchedulerManagementService service) {
        return new PageResponse<>(
                page.items().stream().map(task -> TaskResponse.from(task, service)).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    static PageResponse<RunResponse> runPage(PageResponse<ScheduledTaskRun> page, SchedulerManagementService service) {
        return new PageResponse<>(
                page.items().stream().map(run -> RunResponse.from(run, service)).toList(),
                page.page(),
                page.size(),
                page.total());
    }
}
