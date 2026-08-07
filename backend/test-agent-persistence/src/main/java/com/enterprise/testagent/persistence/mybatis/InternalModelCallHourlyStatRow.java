package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 内部模型代理调用小时聚合行，供 MyBatis 构造函数 resultMap 映射。 */
public record InternalModelCallHourlyStatRow(
        Instant statHour,
        String providerId,
        String model,
        String endpoint,
        String source,
        String outcome,
        Long requestCount,
        Long durationMillisSum,
        Long durationMillisMax) {
}
