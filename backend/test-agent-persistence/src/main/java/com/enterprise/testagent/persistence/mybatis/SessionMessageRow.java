package com.enterprise.testagent.persistence.mybatis;

import java.math.BigDecimal;
import java.time.Instant;

/** session_messages 表行模型，包含分享代操作发送人归因。 */
public record SessionMessageRow(
        String messageId,
        String sessionId,
        String role,
        String content,
        String traceId,
        Instant createdAt,
        String runId,
        String agentId,
        String remoteMessageId,
        String partsJson,
        Long tokensInput,
        Long tokensOutput,
        Long tokensReasoning,
        Long tokensCacheRead,
        Long tokensCacheWrite,
        BigDecimal costUsd,
        Instant updatedAt,
        String sourceType,
        String sourceRefId,
        String senderUserId,
        String senderUnifiedAuthId,
        Boolean sentBySharedUser) {
}
