package com.enterprise.testagent.domain.session;

import java.util.Objects;

/**
 * 用户历史会话列表项，封装会话主体和列表展示所需的工作区上下文。
 */
public record SessionHistoryItem(Session session, SessionWorkspaceContext workspaceContext) {

    public SessionHistoryItem {
        Objects.requireNonNull(session, "session must not be null");
        if (workspaceContext != null && workspaceContext.empty()) {
            workspaceContext = null;
        }
    }
}
