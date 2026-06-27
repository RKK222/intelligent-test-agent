package com.icbc.testagent.opencode.runtime.process;

import java.time.Instant;
import java.util.Objects;

/**
 * 运行管理接口返回的容器指标采样点。
 */
public record RuntimeManagementContainerMetricSample(
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

    public RuntimeManagementContainerMetricSample {
        sampledAt = Objects.requireNonNull(sampledAt, "sampledAt must not be null");
    }
}
