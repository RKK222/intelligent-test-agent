package com.example.testagent.domain.session;

/**
 * 会话状态用于持久化筛选和后续历史会话 API，不影响 Run 状态机。
 */
public enum SessionStatus {
    ACTIVE,
    ARCHIVED
}
