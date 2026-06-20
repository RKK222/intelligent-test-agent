package com.example.testagent.domain.session;

import com.example.testagent.domain.support.DomainValidation;

/**
 * 平台会话消息 ID，使用 msg_ 前缀，避免和 opencode message ID 或数据库主键混用。
 */
public record SessionMessageId(String value) {

    /**
     * 校验平台消息 ID 前缀。
     */
    public SessionMessageId {
        value = DomainValidation.requirePrefixedId(value, "msg_", "messageId");
    }

    /**
     * 返回原始 messageId 字符串，便于日志和错误详情使用。
     */
    @Override
    public String toString() {
        return value;
    }
}
