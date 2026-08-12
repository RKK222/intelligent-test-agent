package com.enterprise.testagent.domain.internalmodelobservability;

/** 由单次调用明细计算出的平均值与五数概括，供总览指标和箱线图共用。 */
public record InternalModelLatencyDistribution(
        long sampleCount,
        Double averageMillis,
        Double minimumMillis,
        Double firstQuartileMillis,
        Double medianMillis,
        Double thirdQuartileMillis,
        Double maximumMillis) {

    public InternalModelLatencyDistribution {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be >= 0");
        }
        if (sampleCount == 0) {
            averageMillis = null;
            minimumMillis = null;
            firstQuartileMillis = null;
            medianMillis = null;
            thirdQuartileMillis = null;
            maximumMillis = null;
        } else if (averageMillis == null
                || minimumMillis == null
                || firstQuartileMillis == null
                || medianMillis == null
                || thirdQuartileMillis == null
                || maximumMillis == null
                || minimumMillis < 0
                || averageMillis < minimumMillis
                || averageMillis > maximumMillis
                || minimumMillis > firstQuartileMillis
                || firstQuartileMillis > medianMillis
                || medianMillis > thirdQuartileMillis
                || thirdQuartileMillis > maximumMillis) {
            throw new IllegalArgumentException("non-empty latency distribution must be complete and ordered");
        }
    }
}
