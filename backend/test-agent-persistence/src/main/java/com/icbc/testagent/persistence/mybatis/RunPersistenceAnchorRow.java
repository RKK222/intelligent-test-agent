package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;

/** runs 表的 Redis 摘要模式锚点行，只包含控制面元数据。 */
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
