package com.icbc.testagent.domain.session;

import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.user.UserId;
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
        Instant updatedAt,
        ConversationSourceType sourceType,
        String sourceRefId,
        UserId senderUserId) {

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
                createdAt,
                ConversationSourceType.MANUAL,
                null,
                null);
    }

    /**
     * 构造带远端快照字段的消息，默认来源为人工发起以兼容旧数据。
     */
    public SessionMessage(
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
        this(
                messageId,
                sessionId,
                role,
                content,
                createdAt,
                traceId,
                runId,
                agentId,
                remoteMessageId,
                partsJson,
                tokenUsage,
                costUsd,
                updatedAt,
                ConversationSourceType.MANUAL,
                null,
                null);
    }

    /**
     * 校验平台会话消息必填字段和可选快照字段，确保空消息不会进入持久化层。
     */
    public SessionMessage {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        // assistant 的工具步骤可能没有正文，但 partsJson 仍需落库供历史文档和文件变更恢复。
        if (role == SessionMessageRole.ASSISTANT
                && content != null
                && content.isBlank()
                && partsJson != null
                && !partsJson.isBlank()) {
            content = "";
        } else {
            content = DomainValidation.requireText(content, "content");
        }
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
        sourceType = sourceType == null ? ConversationSourceType.MANUAL : sourceType;
        if (sourceRefId != null) {
            sourceRefId = DomainValidation.requireText(sourceRefId, "sourceRefId");
        }
    }

    /**
     * 设置消息来源信息，定时任务模拟用户发送时 senderUserId 记录开启该计划的用户。
     */
    public SessionMessage withSource(ConversationSourceType sourceType, String sourceRefId, UserId senderUserId) {
        return new SessionMessage(
                messageId,
                sessionId,
                role,
                content,
                createdAt,
                traceId,
                runId,
                agentId,
                remoteMessageId,
                partsJson,
                tokenUsage,
                costUsd,
                updatedAt,
                sourceType,
                sourceRefId,
                senderUserId);
    }
}
