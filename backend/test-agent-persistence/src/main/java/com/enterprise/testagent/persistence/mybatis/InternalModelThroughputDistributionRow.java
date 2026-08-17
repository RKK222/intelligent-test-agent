package com.enterprise.testagent.persistence.mybatis;

/** MyBatis 输出 Token 吞吐量平均值与五数概括行；单位为 tokens/s。 */
public record InternalModelThroughputDistributionRow(
        Long sampleCount,
        Double averageTokensPerSecond,
        Double minimumTokensPerSecond,
        Double firstQuartileTokensPerSecond,
        Double medianTokensPerSecond,
        Double thirdQuartileTokensPerSecond,
        Double maximumTokensPerSecond) {
}
