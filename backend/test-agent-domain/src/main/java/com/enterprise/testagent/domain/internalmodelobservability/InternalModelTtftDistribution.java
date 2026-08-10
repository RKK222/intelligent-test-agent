package com.enterprise.testagent.domain.internalmodelobservability;

/**
 * TTFT 五数概括。样本只包含确实收到首个模型输出的调用，用于在箱线图中展示整体分布区间。
 */
public record InternalModelTtftDistribution(
        long sampleCount,
        Double minimumMillis,
        Double firstQuartileMillis,
        Double medianMillis,
        Double thirdQuartileMillis,
        Double maximumMillis) {

    public InternalModelTtftDistribution {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be >= 0");
        }
        if (sampleCount == 0) {
            if (minimumMillis != null
                    || firstQuartileMillis != null
                    || medianMillis != null
                    || thirdQuartileMillis != null
                    || maximumMillis != null) {
                throw new IllegalArgumentException("empty distribution must not contain values");
            }
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
            throw new IllegalArgumentException("distribution values must be non-negative and ordered");
        }
    }
}
