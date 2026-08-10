package com.enterprise.testagent.domain.session;

import java.util.Objects;

/**
 * 用户历史会话列表项，封装会话主体和列表展示所需的工作区上下文。
 *
 * <p>{@code shareStatus} 为会话协作分享的派生状态，用于列表直接展示是否已分享及是否过期：
 * 未分享为 {@code null}，已分享为 {@code ACTIVE}/{@code EXPIRED}/{@code REVOKED}。
 * 单会话详情读取等不关注分享状态的调用方可继续使用两参构造方法，默认按未分享处理。
 */
public record SessionHistoryItem(Session session, SessionWorkspaceContext workspaceContext, String shareStatus) {

    public SessionHistoryItem {
        Objects.requireNonNull(session, "session must not be null");
        if (workspaceContext != null && workspaceContext.empty()) {
            workspaceContext = null;
        }
    }

    /**
     * 兼容不关注分享状态的调用方；等价于 {@code shareStatus = null}（未分享）。
     */
    public SessionHistoryItem(Session session, SessionWorkspaceContext workspaceContext) {
        this(session, workspaceContext, null);
    }
}
