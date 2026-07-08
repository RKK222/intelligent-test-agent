package com.icbc.testagent.domain.session;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.user.UserId;

/**
 * 用户级历史会话只读查询端口；新 SQL 统一由 MyBatis XML mapper 实现。
 */
public interface SessionHistoryRepository {

    /**
     * 查询当前用户可见的 ACTIVE 历史会话，用户归因来自会话创建人、Run 触发人或消息发送人。
     */
    PageResponse<SessionHistoryItem> findUserHistory(UserId userId, String query, PageRequest pageRequest);
}
