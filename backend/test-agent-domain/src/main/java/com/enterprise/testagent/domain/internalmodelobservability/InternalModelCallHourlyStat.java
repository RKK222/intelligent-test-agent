package com.enterprise.testagent.domain.internalmodelobservability;

import java.time.Instant;

/** 按小时聚合的调用统计行。outcome 保留字符串以承载任意分类枚举名，兼容未来扩展。 */
public record InternalModelCallHourlyStat(
        Instant statHour,
        String providerId,
        String model,
        String endpoint,
        String source,
        String outcome,
        long requestCount,
        long durationMillisSum,
        long durationMillisMax) {
}
