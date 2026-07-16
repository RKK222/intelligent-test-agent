package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** session_messages 表中的终态摘要行；partsJson 不进入行模型，XML 固定写 NULL。 */
public record RunSummaryRow(
        String messageId,
        String sessionId,
        String role,
        String content,
        String traceId,
        Instant createdAt,
        String runId,
        String agentId,
        String remoteMessageId,
        Instant updatedAt,
        String sourceType,
        String sourceRefId,
        String senderUserId,
        String contentKind,
        String summaryKey,
        int summaryVersion,
        String summaryStatus) {
}
