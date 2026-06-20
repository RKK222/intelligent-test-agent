package com.example.testagent.domain.session;

import com.example.testagent.domain.support.DomainValidation;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * 智能体会话领域对象，关联一个 Workspace，但不承载消息持久化或 opencode 调用逻辑。
 * opencode 映射字段仅供后端 Run 编排复用远端会话，不属于前端 API 契约。
 */
public record Session(
        SessionId sessionId,
        WorkspaceId workspaceId,
        String title,
        SessionStatus status,
        Instant createdAt,
        Instant updatedAt,
        String traceId,
        String opencodeSessionId,
        ExecutionNodeId opencodeExecutionNodeId,
        boolean pinned) {

    /**
     * 创建新的平台会话，默认状态为 ACTIVE，traceId 使用内部占位值。
     */
    public Session(SessionId sessionId, WorkspaceId workspaceId, String title, Instant createdAt) {
        this(sessionId, workspaceId, title, SessionStatus.ACTIVE, createdAt, createdAt, "trace_unspecified");
    }

    /**
     * 构造不带远端 opencode 映射的会话，适用于普通列表、详情和历史兼容读取。
     */
    public Session(
            SessionId sessionId,
            WorkspaceId workspaceId,
            String title,
            SessionStatus status,
            Instant createdAt,
            Instant updatedAt,
            String traceId) {
        this(sessionId, workspaceId, title, status, createdAt, updatedAt, traceId, null, null);
    }

    /**
     * 构造带远端 opencode 映射的会话，默认不置顶；映射只供后端运行编排使用。
     */
    public Session(
            SessionId sessionId,
            WorkspaceId workspaceId,
            String title,
            SessionStatus status,
            Instant createdAt,
            Instant updatedAt,
            String traceId,
            String opencodeSessionId,
            ExecutionNodeId opencodeExecutionNodeId) {
        this(sessionId, workspaceId, title, status, createdAt, updatedAt, traceId, opencodeSessionId, opencodeExecutionNodeId, false);
    }

    /**
     * 校验会话领域不变量：标题、traceId、时间和远端 session/node 映射必须同时有效。
     */
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
        if (opencodeSessionId != null) {
            opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
        }
        if ((opencodeSessionId == null) != (opencodeExecutionNodeId == null)) {
            throw new IllegalArgumentException("opencode session mapping must be set together");
        }
    }

    /**
     * 判断会话是否已有可复用的远端 opencode session 映射；只有 sessionId 和 nodeId 同时存在才为 true。
     */
    public boolean hasOpencodeSessionMapping() {
        return opencodeSessionId != null && opencodeExecutionNodeId != null;
    }

    /**
     * 绑定远端 opencode session 与执行节点，保留平台标题、状态和置顶状态。
     */
    public Session attachOpencodeSession(
            String opencodeSessionId,
            ExecutionNodeId executionNodeId,
            Instant updatedAt,
            String traceId) {
        return new Session(
                sessionId,
                workspaceId,
                title,
                status,
                createdAt,
                updatedAt,
                traceId,
                opencodeSessionId,
                executionNodeId,
                pinned);
    }

    /**
     * 更新平台会话的展示标题和置顶状态，保留内部 opencode 映射。
     */
    public Session updateTitleAndPinned(String title, boolean pinned, Instant updatedAt, String traceId) {
        return new Session(
                sessionId,
                workspaceId,
                title,
                status,
                createdAt,
                updatedAt,
                traceId,
                opencodeSessionId,
                opencodeExecutionNodeId,
                pinned);
    }

    /**
     * 将会话软删除为归档状态；归档后不再置顶，但保留内部映射便于审计和后续兼容读取。
     */
    public Session archive(Instant updatedAt, String traceId) {
        return new Session(
                sessionId,
                workspaceId,
                title,
                SessionStatus.ARCHIVED,
                createdAt,
                updatedAt,
                traceId,
                opencodeSessionId,
                opencodeExecutionNodeId,
                false);
    }
}
