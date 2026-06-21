package com.icbc.testagent.domain.session;

import com.icbc.testagent.domain.support.DomainValidation;

/**
 * Session 领域 ID，使用 ses_ 前缀隔离 opencode generated DTO。
 */
public record SessionId(String value) {

    /**
     * 校验平台会话 ID 前缀。
     */
    public SessionId {
        value = DomainValidation.requirePrefixedId(value, "ses_", "sessionId");
    }

    /**
     * 返回原始 sessionId 字符串，便于日志、事件和持久化参数使用。
     */
    @Override
    public String toString() {
        return value;
    }
}
