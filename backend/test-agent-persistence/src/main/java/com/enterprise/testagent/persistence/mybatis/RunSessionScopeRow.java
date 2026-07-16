package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * run_session_scopes 表行模型。
 */
public record RunSessionScopeRow(
        String runId,
        String rootSessionId,
        long scopeVersion,
        String traceId,
        String metadataJson,
        Instant createdAt,
        Instant updatedAt) {
}
