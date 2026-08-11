package com.enterprise.testagent.domain.internalmodelobservability;

/** 由单次调用明细计算出的时延五数概括，供箱线图展示。 */
public record InternalModelLatencyDistribution(
        long sampleCount,
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
            minimumMillis = null;
            firstQuartileMillis = null;
            medianMillis = null;
            thirdQuartileMillis = null;
            maximumMillis = null;
        } else if (minimumMillis == null
                || firstQuartileMillis == null
                || medianMillis == null
                || thirdQuartileMillis == null
                || maximumMillis == null
                || minimumMillis < 0
                || minimumMillis > firstQuartileMillis
                || firstQuartileMillis > medianMillis
                || medianMillis > thirdQuartileMillis
                || thirdQuartileMillis > maximumMillis) {
            throw new IllegalArgumentException("non-empty latency distribution must be complete and ordered");
        }
    }
}
