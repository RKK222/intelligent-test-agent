package com.enterprise.testagent.persistence.mybatis;

/** MyBatis TTFT 五数概括行；空样本时仅 sampleCount 为 0，其余字段为空。 */
public record InternalModelTtftDistributionRow(
        Long sampleCount,
        Double minimumMillis,
        Double firstQuartileMillis,
        Double medianMillis,
        Double thirdQuartileMillis,
        Double maximumMillis) {
}
