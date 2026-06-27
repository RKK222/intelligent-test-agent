package com.icbc.testagent.domain.opencodeprocess;

import java.time.Instant;
import java.util.Objects;

/**
 * Redis 中按时间排序保存的容器运行指标样本。
 */
public record ContainerRuntimeMetricSample(
        Instant sampledAt,
        int maxProcesses,
        int currentProcesses,
        String metricsSource,
        Double cpuUsagePercent,
        Long memoryMaxBytes,
        Long memoryUsedBytes,
        Double memoryUsagePercent,
        Double diskReadBytesPerSecond,
        Double diskWriteBytesPerSecond) {

    public ContainerRuntimeMetricSample {
        sampledAt = Objects.requireNonNull(sampledAt, "sampledAt must not be null");
    }
}
