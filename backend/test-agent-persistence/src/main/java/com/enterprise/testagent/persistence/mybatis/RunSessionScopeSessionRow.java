package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * run_session_scope_sessions 表行模型。
 */
public record RunSessionScopeSessionRow(
        String runId,
        String sessionId,
        String rootSessionId,
        String parentSessionId,
        boolean childSession,
        String discoverySource,
        String taskMessageId,
        String taskPartId,
        String taskCallId,
        String traceId,
        String metadataJson,
        Instant discoveredAt,
        Instant updatedAt) {
}
