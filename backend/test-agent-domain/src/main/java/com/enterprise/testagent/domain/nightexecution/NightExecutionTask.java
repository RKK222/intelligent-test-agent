package com.enterprise.testagent.domain.nightexecution;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/** 夜间任务聚合；完整 Run 输入只在待执行阶段保存，投递使用 attemptId 防止旧执行者回写。 */
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
        String dispatchAttemptId,
        String dispatchOwnerBackendProcessId,
        Instant dispatchLeaseUntil,
        long stateVersion,
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
        if (!slotStart.isBefore(slotEnd) || slotEnd.isAfter(windowEnd)) {
            throw new IllegalArgumentException("invalid slot range");
        }
        if (rolloverCount < 0) throw new IllegalArgumentException("rolloverCount must not be negative");
        if (stateVersion < 0) throw new IllegalArgumentException("stateVersion must not be negative");
        clientRequestId = required(clientRequestId, "clientRequestId");
        sessionTitle = required(sessionTitle, "sessionTitle");
        contentPreview = required(contentPreview, "contentPreview");
        targetLinuxServerId = required(targetLinuxServerId, "targetLinuxServerId");
        dispatchAttemptId = optional(dispatchAttemptId);
        dispatchOwnerBackendProcessId = optional(dispatchOwnerBackendProcessId);
        traceId = required(traceId, "traceId");
        if (status == NightExecutionTaskStatus.DISPATCHING
                && (dispatchStartedAt == null || dispatchAttemptId == null
                || dispatchOwnerBackendProcessId == null || dispatchLeaseUntil == null)) {
            throw new IllegalArgumentException("dispatching task requires attempt owner and lease");
        }
    }

    /** 兼容迁移前调用方；新增的投递租约字段使用空值，状态版本从零开始。 */
    public NightExecutionTask(
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
        this(taskId, ownerUserId, sessionId, workspaceId, clientRequestId, sessionTitle, contentPreview,
                runInputJson, status, slotStart, slotEnd, windowEnd, targetLinuxServerId,
                scheduledTaskRunId, runId, rolloverCount, taskCreatedSession, dispatchStartedAt,
                null, null, null, 0L, dismissedAt, reservationReleasedAt, errorCode, errorMessage,
                traceId, createdAt, updatedAt);
    }

    /** 构造持久投递认领；真正并发裁决由仓储按状态、版本和目标服务器执行 CAS。 */
    public NightExecutionTask startDispatch(
            String attemptId,
            String ownerBackendProcessId,
            Instant leaseUntil,
            Instant now) {
        if (status != NightExecutionTaskStatus.SCHEDULED) throw invalid("开始投递");
        Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
        if (!leaseUntil.isAfter(now)) throw new IllegalArgumentException("leaseUntil must be after now");
        return copy(NightExecutionTaskStatus.DISPATCHING, slotStart, slotEnd, targetLinuxServerId,
                null, runId, rolloverCount, now, required(attemptId, "attemptId"),
                required(ownerBackendProcessId, "ownerBackendProcessId"), leaseUntil,
                dismissedAt, reservationReleasedAt, null, null, runInputJson,
                stateVersion + 1, now);
    }

    /** 续租不改变业务状态版本，最终写回仍由同一个 attemptId fencing。 */
    public NightExecutionTask renewDispatchLease(Instant leaseUntil, Instant now) {
        if (status != NightExecutionTaskStatus.DISPATCHING) throw invalid("续租投递");
        if (leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
        return copy(status, slotStart, slotEnd, targetLinuxServerId, scheduledTaskRunId, runId,
                rolloverCount, dispatchStartedAt, dispatchAttemptId, dispatchOwnerBackendProcessId,
                leaseUntil, dismissedAt, reservationReleasedAt, errorCode, errorMessage,
                runInputJson, stateVersion, now);
    }

    /** Run 已受理后清除完整输入，并记录容量已释放，Run 终态不再反向修改本状态。 */
    public NightExecutionTask dispatched(RunId acceptedRunId, Instant now) {
        Objects.requireNonNull(acceptedRunId, "runId must not be null");
        if (status != NightExecutionTaskStatus.DISPATCHING) throw invalid("完成投递");
        return copy(NightExecutionTaskStatus.DISPATCHED, slotStart, slotEnd, targetLinuxServerId,
                scheduledTaskRunId, acceptedRunId, rolloverCount, dispatchStartedAt,
                dispatchAttemptId, dispatchOwnerBackendProcessId, dispatchLeaseUntil,
                dismissedAt, now, null, null, null, stateVersion + 1, now);
    }

    /** 用户仅可取消尚未被目标 Java 认领的任务。 */
    public NightExecutionTask cancel(Instant now) {
        if (status != NightExecutionTaskStatus.SCHEDULED) throw invalid("取消");
        return copy(NightExecutionTaskStatus.CANCELLED, slotStart, slotEnd, targetLinuxServerId,
                scheduledTaskRunId, runId, rolloverCount, dispatchStartedAt, dispatchAttemptId,
                dispatchOwnerBackendProcessId, dispatchLeaseUntil, dismissedAt, now,
                null, null, null, stateVersion + 1, now);
    }

    /** 调整待执行时段；目标服务器保持提交时快照，旧投递认领信息必须清空。 */
    public NightExecutionTask reschedule(
            Instant newSlotStart,
            Instant newSlotEnd,
            String newTargetLinuxServerId,
            Instant now) {
        if (!status.pending()) throw invalid("调整时段");
        if (!newSlotStart.isBefore(newSlotEnd) || newSlotEnd.isAfter(windowEnd)) {
            throw new IllegalArgumentException("invalid rescheduled slot");
        }
        return copy(NightExecutionTaskStatus.SCHEDULED, newSlotStart, newSlotEnd,
                required(newTargetLinuxServerId, "targetLinuxServerId"), null, null,
                rolloverCount, null, null, null, null, dismissedAt, null,
                null, null, runInputJson, stateVersion + 1, now);
    }

    /** 当前 attempt 已确认未创建 Run 时，恢复为原时段的待执行状态。 */
    public NightExecutionTask retryDispatch(Instant now) {
        if (status != NightExecutionTaskStatus.DISPATCHING) throw invalid("恢复待执行");
        return copy(NightExecutionTaskStatus.SCHEDULED, slotStart, slotEnd, targetLinuxServerId,
                null, null, rolloverCount, null, null, null, null,
                dismissedAt, reservationReleasedAt, null, null, runInputJson,
                stateVersion + 1, now);
    }

    /** 最终失败解除容量占位并清除完整输入；对外只保留安全错误。 */
    public NightExecutionTask fail(String code, String message, Instant now) {
        if (!status.pending()) throw invalid("标记失败");
        return copy(NightExecutionTaskStatus.FAILED, slotStart, slotEnd, targetLinuxServerId,
                scheduledTaskRunId, runId, rolloverCount, dispatchStartedAt, dispatchAttemptId,
                dispatchOwnerBackendProcessId, dispatchLeaseUntil, dismissedAt, now,
                required(code, "errorCode"), bounded(message, 500), null,
                stateVersion + 1, now);
    }

    /** 用户关闭失败卡；重复关闭保持幂等。 */
    public NightExecutionTask dismiss(Instant now) {
        if (status != NightExecutionTaskStatus.FAILED) throw invalid("关闭失败卡");
        if (dismissedAt != null) return this;
        return copy(status, slotStart, slotEnd, targetLinuxServerId, scheduledTaskRunId, runId,
                rolloverCount, dispatchStartedAt, dispatchAttemptId, dispatchOwnerBackendProcessId,
                dispatchLeaseUntil, now, reservationReleasedAt, errorCode, errorMessage,
                runInputJson, stateVersion + 1, now);
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
            String nextDispatchAttemptId,
            String nextDispatchOwnerBackendProcessId,
            Instant nextDispatchLeaseUntil,
            Instant nextDismissedAt,
            Instant nextReservationReleasedAt,
            String nextErrorCode,
            String nextErrorMessage,
            String nextRunInputJson,
            long nextStateVersion,
            Instant now) {
        return new NightExecutionTask(
                taskId, ownerUserId, sessionId, workspaceId, clientRequestId, sessionTitle, contentPreview,
                nextRunInputJson, nextStatus, nextSlotStart, nextSlotEnd, windowEnd, nextTargetLinuxServerId,
                nextScheduledRunId, nextRunId, nextRolloverCount, taskCreatedSession, nextDispatchStartedAt,
                nextDispatchAttemptId, nextDispatchOwnerBackendProcessId, nextDispatchLeaseUntil,
                nextStateVersion, nextDismissedAt, nextReservationReleasedAt, nextErrorCode,
                nextErrorMessage, traceId, createdAt, now);
    }

    private IllegalStateException invalid(String action) {
        return new IllegalStateException("夜间任务状态 " + status + " 不允许" + action);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String bounded(String value, int maxLength) {
        if (value == null || value.isBlank()) return "夜间任务启动失败";
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
