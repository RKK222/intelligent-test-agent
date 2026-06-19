package com.example.testagent.domain.session;

import com.example.testagent.domain.support.DomainValidation;
import com.example.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * 智能体会话领域对象，关联一个 Workspace，但不承载消息持久化或 opencode 调用逻辑。
 */
public record Session(
        SessionId sessionId,
        WorkspaceId workspaceId,
        String title,
        SessionStatus status,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    public Session(SessionId sessionId, WorkspaceId workspaceId, String title, Instant createdAt) {
        this(sessionId, workspaceId, title, SessionStatus.ACTIVE, createdAt, createdAt, "trace_unspecified");
    }

    public Session {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        title = DomainValidation.requireText(title, "title");
        Objects.requireNonNull(status, "status must not be null");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
