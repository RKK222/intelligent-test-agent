package com.enterprise.testagent.domain.session;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Optional;

/**
 * 用户级历史会话只读查询端口；新 SQL 统一由 MyBatis XML mapper 实现。
 */
public interface SessionHistoryRepository {

    /**
     * 查询当前用户可见的 ACTIVE 历史会话，用户归因来自会话创建人、Run 触发人或消息发送人。
     */
    PageResponse<SessionHistoryItem> findUserHistory(UserId userId, String query, PageRequest pageRequest);

    /** 按同一归因规则校验并读取单个 ACTIVE 会话，避免仅凭 sessionId 越权访问。 */
    Optional<SessionHistoryItem> findUserSession(UserId userId, SessionId sessionId);

    /** 按同一用户归因规则分页查询指定工作区的会话。 */
    PageResponse<SessionHistoryItem> findUserWorkspaceHistory(
            UserId userId,
            com.enterprise.testagent.domain.workspace.WorkspaceId workspaceId,
            PageRequest pageRequest);
}
