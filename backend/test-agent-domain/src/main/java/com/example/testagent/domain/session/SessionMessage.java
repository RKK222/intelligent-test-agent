package com.example.testagent.domain.session;

import com.example.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * 平台保存的会话消息，不直接暴露或复用 opencode generated message DTO。
 */
public record SessionMessage(
        SessionMessageId messageId,
        SessionId sessionId,
        SessionMessageRole role,
        String content,
        Instant createdAt,
        String traceId) {

    /**
     * 校验平台会话消息必填字段，确保空消息不会进入持久化层。
     */
    public SessionMessage {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        content = DomainValidation.requireText(content, "content");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
