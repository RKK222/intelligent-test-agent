package com.enterprise.testagent.persistence.mybatis;

/** Redis 运营 stream 候选 Run 及消息实际发送人归属快照。 */
public record AnalyticsRedisOutboxRunRow(
        String runId,
        String lastStreamId,
        String userId,
        String username,
        String organization,
        String rdDepartment,
        String department,
        String sessionId,
        String workspaceId,
        String agentId,
        String modelId) {
}
