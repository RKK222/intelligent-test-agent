package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * Run 耗时事实行，持久层只读取时间戳，耗时差值在 Java 中计算以兼容不同数据库方言。
 */
public record AnalyticsDurationFactRow(
        Instant bucketStart,
        String userId,
        String username,
        String organization,
        String rdDepartment,
        String department,
        String workspaceId,
        String agentId,
        String modelId,
        Instant createdAt,
        Instant updatedAt) {
}
