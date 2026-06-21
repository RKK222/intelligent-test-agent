package com.icbc.testagent.domain.session;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Optional;

/**
 * Session 持久化端口，避免应用层直接依赖具体 JDBC Repository。
 */
public interface SessionRepository {

    /**
     * 保存平台会话。
     */
    Session save(Session session);

    /**
     * 按会话 ID 查询会话。
     */
    Optional<Session> findById(SessionId sessionId);

    /**
     * 全局分页搜索会话。
     */
    PageResponse<Session> findPage(String query, PageRequest pageRequest);

    /**
     * 按工作区分页查询会话。
     */
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
