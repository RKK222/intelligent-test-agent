package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 内部模型 provider 探活状态行，供 MyBatis 构造函数 resultMap 映射。 */
public record InternalModelProbeStatusRow(
        String providerId,
        String lastOutcome,
        Integer lastHttpStatus,
        String lastErrorClass,
        Long lastDurationMillis,
        Instant lastProbedAt,
        Instant lastSuccessAt,
        Integer consecutiveFailures,
        String traceId) {
}
