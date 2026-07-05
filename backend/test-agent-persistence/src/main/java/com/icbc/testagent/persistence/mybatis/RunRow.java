package com.icbc.testagent.persistence.mybatis;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * runs 表行模型，仅在 MyBatis RunRepository 内部使用。
 */
public record RunRow(
        String runId,
        String sessionId,
        String workspaceId,
        String status,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Long tokensInput,
        Long tokensOutput,
        Long tokensReasoning,
        Long tokensCacheRead,
        Long tokensCacheWrite,
        BigDecimal costUsd,
        String sourceType,
        String sourceRefId,
        String triggeredByUserId,
        String agentId,
        String modelId) {
}
