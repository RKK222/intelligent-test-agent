package com.example.testagent.domain.session;

import com.example.testagent.common.pagination.PageRequest;
import com.example.testagent.common.pagination.PageResponse;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Optional;

/**
 * Session 持久化端口，避免应用层直接依赖具体 JDBC Repository。
 */
public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findById(SessionId sessionId);

    PageResponse<Session> findByWorkspaceId(WorkspaceId workspaceId, PageRequest pageRequest);

    /**
     * 保存平台会话到远端 opencode 会话的内部映射，供后续 Run 复用同一远端上下文。
     */
    Optional<Session> attachOpencodeSession(
            SessionId sessionId,
            String opencodeSessionId,
            ExecutionNodeId executionNodeId,
            Instant updatedAt,
            String traceId);
}
