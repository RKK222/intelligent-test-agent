package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * RunEvent 表行模型，仅在 MyBatis Repository 内部使用，包含结构化 scope 列。
 */
public record RunEventRow(
        String eventId,
        String runId,
        long seq,
        String type,
        String traceId,
        Instant occurredAt,
        String payloadJson,
        String rootSessionId,
        String sessionId,
        String parentSessionId,
        boolean childSession,
        Long scopeVersion,
        String taskMessageId,
        String taskPartId,
        String taskCallId,
        String rawEventId) {
}
