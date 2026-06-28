package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * analytics_rollup_watermarks 表行模型。
 */
public record AnalyticsFreshnessRow(
        String jobName,
        Instant watermarkAt,
        Instant generatedAt,
        String status,
        String message) {
}
