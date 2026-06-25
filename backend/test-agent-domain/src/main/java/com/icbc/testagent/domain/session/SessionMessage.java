package com.icbc.testagent.domain.session;

import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.TokenUsage;
import java.math.BigDecimal;
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
        String traceId,
        RunId runId,
        String agentId,
        String remoteMessageId,
        String partsJson,
        TokenUsage tokenUsage,
        BigDecimal costUsd,
        Instant updatedAt) {

    /**
     * 构造旧版纯文本消息，兼容既有用户输入和历史数据读取路径。
     */
    public SessionMessage(
            SessionMessageId messageId,
            SessionId sessionId,
            SessionMessageRole role,
            String content,
            Instant createdAt,
            String traceId) {
        this(
                messageId,
                sessionId,
                role,
                content,
                createdAt,
                traceId,
                null,
                null,
                null,
                null,
                TokenUsage.empty(),
                null,
                createdAt);
    }

    /**
     * 校验平台会话消息必填字段和可选快照字段，确保空消息不会进入持久化层。
     */
    public SessionMessage {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        content = DomainValidation.requireText(content, "content");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (agentId != null) {
            agentId = DomainValidation.requireText(agentId, "agentId").trim().toLowerCase(java.util.Locale.ROOT);
        }
        if (remoteMessageId != null) {
            remoteMessageId = DomainValidation.requireText(remoteMessageId, "remoteMessageId");
        }
        if (partsJson != null && partsJson.isBlank()) {
            partsJson = null;
        }
        tokenUsage = tokenUsage == null ? TokenUsage.empty() : tokenUsage;
        if (costUsd != null && costUsd.signum() < 0) {
            throw new IllegalArgumentException("costUsd must not be negative");
        }
        updatedAt = updatedAt == null ? createdAt : DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
