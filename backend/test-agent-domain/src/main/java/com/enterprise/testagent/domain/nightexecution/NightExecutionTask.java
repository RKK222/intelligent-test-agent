package com.enterprise.testagent.domain.nightexecution;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/** 夜间任务聚合；完整 Run 输入只在 runInputJson 中短期保存。 */
public record NightExecutionTask(
        NightExecutionTaskId taskId,
        UserId ownerUserId,
        SessionId sessionId,
        WorkspaceId workspaceId,
        String clientRequestId,
        String sessionTitle,
        String contentPreview,
        String runInputJson,
        NightExecutionTaskStatus status,
        Instant slotStart,
        Instant slotEnd,
        Instant windowEnd,
        String targetLinuxServerId,
        ScheduledTaskRunId scheduledTaskRunId,
        RunId runId,
        int rolloverCount,
        boolean taskCreatedSession,
        Instant dispatchStartedAt,
        Instant dismissedAt,
        Instant reservationReleasedAt,
        String errorCode,
        String errorMessage,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {

    public NightExecutionTask {
        Objects.requireNonNull(taskId); Objects.requireNonNull(ownerUserId); Objects.requireNonNull(sessionId);
        Objects.requireNonNull(workspaceId); Objects.requireNonNull(status); Objects.requireNonNull(slotStart);
        Objects.requireNonNull(slotEnd); Objects.requireNonNull(windowEnd); Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (!slotStart.isBefore(slotEnd) || slotEnd.isAfter(windowEnd)) throw new IllegalArgumentException("invalid slot range");
        if (rolloverCount < 0) throw new IllegalArgumentException("rolloverCount must not be negative");
        clientRequestId = required(clientRequestId, "clientRequestId");
        sessionTitle = required(sessionTitle, "sessionTitle");
        contentPreview = required(contentPreview, "contentPreview");
        targetLinuxServerId = required(targetLinuxServerId, "targetLinuxServerId");
        traceId = required(traceId, "traceId");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    /** 绑定 scheduler USER_PLAN 运行记录；创建事务完成前任务仍保持待执行。 */
    public NightExecutionTask withScheduledRun(ScheduledTaskRunId taskRunId, Instant now) {
        if (status != NightExecutionTaskStatus.SCHEDULED) throw invalid("绑定调度记录");
        return copy(status, slotStart, slotEnd, targetLinuxServerId, taskRunId, runId, rolloverCount,
                dispatchStartedAt, dismissedAt, reservationReleasedAt, errorCode, errorMessage, runInputJson, now);
    }

    /** 原子认领前构造启动中状态，真正并发裁决由仓储按旧状态条件更新。 */
    public NightExecutionTask startDispatch(Instant now) {
        if (status != NightExecutionTaskStatus.SCHEDULED) throw invalid("开始投递");
        return copy(NightExecutionTaskStatus.DISPATCHING, slotStart, slotEnd, targetLinuxServerId,
                scheduledTaskRunId, runId, rolloverCount, now, dismissedAt, reservationReleasedAt,
                null, null, runInputJson, now);
    }

    /** Run 已创建后清除完整输入，仅保留安全预览和审计字段。 */
    public NightExecutionTask dispatched(RunId runId, Instant now) {
        Objects.requireNonNull(runId, "runId must not be null");
        if (status != NightExecutionTaskStatus.DISPATCHING) throw invalid("完成投递");
        return copy(NightExecutionTaskStatus.DISPATCHED, slotStart, slotEnd, targetLinuxServerId,
                scheduledTaskRunId, runId, rolloverCount, dispatchStartedAt, dismissedAt,
                reservationReleasedAt, null, null, null, now);
    }

    /** 用户仅可取消尚未被调度器认领的任务。 */
    public NightExecutionTask cancel(Instant now) {
        if (status != NightExecutionTaskStatus.SCHEDULED) throw invalid("取消");
        return copy(NightExecutionTaskStatus.CANCELLED, slotStart, slotEnd, targetLinuxServerId,
                scheduledTaskRunId, runId, rolloverCount, dispatchStartedAt, dismissedAt, now,
                null, null, null, now);
    }

    /** 在同一窗口内顺延并关联新的 USER_PLAN 运行记录。 */
    public NightExecutionTask reschedule(
            Instant newSlotStart,
            Instant newSlotEnd,
            String newTargetLinuxServerId,
            ScheduledTaskRunId newScheduledRunId,
            Instant now) {
        if (!status.pending()) throw invalid("调整时段");
        if (!newSlotStart.isBefore(newSlotEnd) || newSlotEnd.isAfter(windowEnd)) {
            throw new IllegalArgumentException("invalid rescheduled slot");
        }
        return copy(NightExecutionTaskStatus.SCHEDULED, newSlotStart, newSlotEnd,
                required(newTargetLinuxServerId, "targetLinuxServerId"), newScheduledRunId, null,
                rolloverCount, null, dismissedAt, null,
                null, null, runInputJson, now);
    }

    /** 系统因错过原时段而顺延，唯一会增加 rolloverCount 的状态迁移。 */
    public NightExecutionTask rollover(
            Instant newSlotStart,
            Instant newSlotEnd,
            String newTargetLinuxServerId,
            ScheduledTaskRunId newScheduledRunId,
            Instant now) {
        NightExecutionTask moved = reschedule(
                newSlotStart, newSlotEnd, newTargetLinuxServerId, newScheduledRunId, now);
        return moved.copy(
                moved.status, moved.slotStart, moved.slotEnd, moved.targetLinuxServerId,
                moved.scheduledTaskRunId, moved.runId, rolloverCount + 1, moved.dispatchStartedAt,
                moved.dismissedAt, moved.reservationReleasedAt, moved.errorCode, moved.errorMessage,
                moved.runInputJson, now);
    }

    /** 最终失败解除锁定并清除完整输入；对外只保留安全错误。 */
    public NightExecutionTask fail(String code, String message, Instant now) {
        if (!status.pending()) throw invalid("标记失败");
        return copy(NightExecutionTaskStatus.FAILED, slotStart, slotEnd, targetLinuxServerId,
                scheduledTaskRunId, runId, rolloverCount, dispatchStartedAt, dismissedAt, now,
                required(code, "errorCode"), bounded(message, 500), null, now);
    }

    /** 用户关闭失败卡；重复关闭保持幂等。 */
    public NightExecutionTask dismiss(Instant now) {
        if (status != NightExecutionTaskStatus.FAILED) throw invalid("关闭失败卡");
        if (dismissedAt != null) return this;
        return copy(status, slotStart, slotEnd, targetLinuxServerId, scheduledTaskRunId, runId,
                rolloverCount, dispatchStartedAt, now, reservationReleasedAt, errorCode, errorMessage,
                runInputJson, now);
    }

    private NightExecutionTask copy(
            NightExecutionTaskStatus nextStatus,
            Instant nextSlotStart,
            Instant nextSlotEnd,
            String nextTargetLinuxServerId,
            ScheduledTaskRunId nextScheduledRunId,
            RunId nextRunId,
            int nextRolloverCount,
            Instant nextDispatchStartedAt,
            Instant nextDismissedAt,
            Instant nextReservationReleasedAt,
            String nextErrorCode,
            String nextErrorMessage,
            String nextRunInputJson,
            Instant now) {
        return new NightExecutionTask(
                taskId, ownerUserId, sessionId, workspaceId, clientRequestId, sessionTitle, contentPreview,
                nextRunInputJson, nextStatus, nextSlotStart, nextSlotEnd, windowEnd, nextTargetLinuxServerId,
                nextScheduledRunId, nextRunId, nextRolloverCount, taskCreatedSession, nextDispatchStartedAt,
                nextDismissedAt, nextReservationReleasedAt, nextErrorCode, nextErrorMessage, traceId,
                createdAt, now);
    }

    private IllegalStateException invalid(String action) {
        return new IllegalStateException("夜间任务状态 " + status + " 不允许" + action);
    }

    private static String bounded(String value, int maxLength) {
        if (value == null || value.isBlank()) return "夜间任务启动失败";
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
