package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * Run 耗时直方图行模型，leMs 是桶上界，runCount 是该桶内数量。
 */
public record AnalyticsDurationHistogramRow(
        Instant bucketStart,
        String organization,
        String rdDepartment,
        String department,
        String workspaceId,
        String agentId,
        String modelId,
        long leMs,
        long runCount) {
}
