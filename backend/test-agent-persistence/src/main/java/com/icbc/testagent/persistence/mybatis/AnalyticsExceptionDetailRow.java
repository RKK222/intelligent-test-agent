package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 异常 Run 明细查询行，只返回运行元数据和归属信息。
 */
public record AnalyticsExceptionDetailRow(
        String runId,
        String userId,
        String username,
        String organization,
        String rdDepartment,
        String department,
        String workspaceId,
        String agentId,
        String modelId,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
