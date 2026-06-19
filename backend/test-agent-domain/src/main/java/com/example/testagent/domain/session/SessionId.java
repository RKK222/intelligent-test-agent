package com.example.testagent.domain.session;

import com.example.testagent.domain.support.DomainValidation;

/**
 * Session 领域 ID，使用 ses_ 前缀隔离 opencode generated DTO。
 */
public record SessionId(String value) {

    public SessionId {
        value = DomainValidation.requirePrefixedId(value, "ses_", "sessionId");
    }

    @Override
    public String toString() {
        return value;
    }
}
