package com.enterprise.testagent.persistence.mybatis;

/** MyBatis 时延平均值与五数概括行；空样本时仅 sampleCount 为 0，其余字段为空。 */
public record InternalModelLatencyDistributionRow(
        Long sampleCount,
        Double averageMillis,
        Double minimumMillis,
        Double firstQuartileMillis,
        Double medianMillis,
        Double thirdQuartileMillis,
        Double maximumMillis) {
}
