package com.enterprise.testagent.domain.internalmodelobservability;

import java.time.Instant;

/** 每 provider 一行，记录最近一次探活结果与连续失败次数，供前端健康卡片直接读取。 */
public record InternalModelProbeStatus(
        String providerId,
        InternalModelCallOutcome lastOutcome,
        Integer lastHttpStatus,
        String lastErrorClass,
        Long lastDurationMillis,
        Instant lastProbedAt,
        Instant lastSuccessAt,
        int consecutiveFailures,
        String traceId) {
}
