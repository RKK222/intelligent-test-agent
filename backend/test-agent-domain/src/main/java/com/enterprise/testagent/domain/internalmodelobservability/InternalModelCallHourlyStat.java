package com.enterprise.testagent.domain.internalmodelobservability;

import java.time.Instant;

/**
 * 按小时聚合的调用统计行。首 token 与流完成三元组只统计对应时刻存在的调用，供准确计算平均/最大值；
 * outcome 保留字符串以承载任意分类枚举名，兼容未来扩展。
 */
public record InternalModelCallHourlyStat(
        Instant statHour,
        String providerId,
        String model,
        String endpoint,
        String source,
        String outcome,
        long requestCount,
        long durationMillisSum,
        long durationMillisMax,
        long firstTokenMillisSum,
        long firstTokenMillisMax,
        long firstTokenCount,
        long streamCompleteMillisSum,
        long streamCompleteMillisMax,
        long streamCompleteCount) {
}
