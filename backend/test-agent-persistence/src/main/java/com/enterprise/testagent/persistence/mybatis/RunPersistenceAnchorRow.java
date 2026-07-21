package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** runs 表的控制面锚点行，覆盖 Redis 摘要模式与 legacy Scheduled Run，且不包含对话原文。 */
public record RunPersistenceAnchorRow(
        String runId,
        String sessionId,
        String workspaceId,
        String status,
        String storageMode,
        long statusVersion,
        String clientRequestId,
        String producerLinuxServerId,
        String executionNodeIdSnapshot,
        String opencodeProcessIdSnapshot,
        String rootRemoteSessionId,
        String dispatchMessageId,
        String scheduledDispatchAttemptId,
        Instant scheduledDispatchLeaseUntil,
        Instant scheduledDispatchAcceptedAt,
        String assistantSummaryMessageId,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Instant detailsExpiresAt,
        String sourceType,
        String sourceRefId,
        String triggeredByUserId,
        String agentId,
        String modelId) {
}
