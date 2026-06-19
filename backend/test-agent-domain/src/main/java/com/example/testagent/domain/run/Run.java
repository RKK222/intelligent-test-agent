package com.example.testagent.domain.run;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Run 聚合根，封装运行状态迁移规则；持久化和执行节点选择由后续阶段适配。
 */
public record Run(
        RunId runId,
        SessionId sessionId,
        WorkspaceId workspaceId,
        RunStatus status,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    public Run(
            RunId runId,
            SessionId sessionId,
            WorkspaceId workspaceId,
            RunStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this(runId, sessionId, workspaceId, status, createdAt, updatedAt, "trace_unspecified");
    }

    public Run {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        traceId = com.example.testagent.domain.support.DomainValidation.requireText(traceId, "traceId");
    }

    public Run transitionTo(RunStatus nextStatus, Instant nextUpdatedAt) {
        Objects.requireNonNull(nextStatus, "nextStatus must not be null");
        Objects.requireNonNull(nextUpdatedAt, "nextUpdatedAt must not be null");
        if (!status.canTransitionTo(nextStatus)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Run 状态不允许从 " + status + " 流转到 " + nextStatus,
                    Map.of("currentStatus", status.name(), "nextStatus", nextStatus.name()));
        }
        return new Run(runId, sessionId, workspaceId, nextStatus, createdAt, nextUpdatedAt, traceId);
    }

    public Run start(Instant nextUpdatedAt) {
        return transitionTo(RunStatus.RUNNING, nextUpdatedAt);
    }

    public Run requestCancel(Instant nextUpdatedAt) {
        if (status == RunStatus.PENDING) {
            return transitionTo(RunStatus.CANCELLED, nextUpdatedAt);
        }
        return transitionTo(RunStatus.CANCELLING, nextUpdatedAt);
    }

    public Run succeed(Instant nextUpdatedAt) {
        return transitionTo(RunStatus.SUCCEEDED, nextUpdatedAt);
    }

    public Run fail(Instant nextUpdatedAt) {
        return transitionTo(RunStatus.FAILED, nextUpdatedAt);
    }

    public Run cancel(Instant nextUpdatedAt) {
        return transitionTo(RunStatus.CANCELLED, nextUpdatedAt);
    }
}
