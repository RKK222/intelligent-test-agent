package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 由 PostgreSQL JSONB 函数生成的脱敏历史能力/反馈事件。 */
public record AnalyticsBackfillEventRow(
        String eventId,
        long eventVersion,
        String sourceType,
        String aggregateId,
        Instant occurredAt,
        String payloadJson) {
}
