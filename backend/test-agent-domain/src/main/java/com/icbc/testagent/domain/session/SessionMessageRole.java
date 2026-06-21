package com.icbc.testagent.domain.session;

/**
 * 平台会话消息角色，初版只保存前端和 Run 编排需要的稳定角色。
 */
public enum SessionMessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
