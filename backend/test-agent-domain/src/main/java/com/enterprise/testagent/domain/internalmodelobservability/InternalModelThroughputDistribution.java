package com.enterprise.testagent.domain.internalmodelobservability;

/** 由单次完整流计算出的输出 Token 吞吐量平均值与五数概括，单位为 tokens/s。 */
public record InternalModelThroughputDistribution(
        long sampleCount,
        Double averageTokensPerSecond,
        Double minimumTokensPerSecond,
        Double firstQuartileTokensPerSecond,
        Double medianTokensPerSecond,
        Double thirdQuartileTokensPerSecond,
        Double maximumTokensPerSecond) {

    public InternalModelThroughputDistribution {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be >= 0");
        }
        if (sampleCount == 0) {
            averageTokensPerSecond = null;
            minimumTokensPerSecond = null;
            firstQuartileTokensPerSecond = null;
            medianTokensPerSecond = null;
            thirdQuartileTokensPerSecond = null;
            maximumTokensPerSecond = null;
        } else if (averageTokensPerSecond == null
                || minimumTokensPerSecond == null
                || firstQuartileTokensPerSecond == null
                || medianTokensPerSecond == null
                || thirdQuartileTokensPerSecond == null
                || maximumTokensPerSecond == null
                || minimumTokensPerSecond < 0
                || averageTokensPerSecond < minimumTokensPerSecond
                || averageTokensPerSecond > maximumTokensPerSecond
                || minimumTokensPerSecond > firstQuartileTokensPerSecond
                || firstQuartileTokensPerSecond > medianTokensPerSecond
                || medianTokensPerSecond > thirdQuartileTokensPerSecond
                || thirdQuartileTokensPerSecond > maximumTokensPerSecond) {
            throw new IllegalArgumentException("non-empty throughput distribution must be complete and ordered");
        }
    }
}
