package com.icbc.testagent.opencode.runtime.process;

import java.time.Instant;
import java.util.Objects;

/**
 * 运行管理接口返回的后端 Java 进程指标采样点。
 */
public record RuntimeManagementBackendMetricSample(
        Instant sampledAt,
        Double cpuUsagePercent,
        Long memoryMaxBytes,
        Long memoryUsedBytes,
        Double memoryUsagePercent,
        Long diskMaxBytes,
        Long diskUsedBytes,
        Double diskUsagePercent,
        Long jvmMemoryUsedBytes,
        Long jvmMemoryCommittedBytes,
        Long jvmMemoryMaxBytes,
        Long jvmGcPauseMillis,
        Integer jvmThreadsLive) {

    public RuntimeManagementBackendMetricSample {
        sampledAt = Objects.requireNonNull(sampledAt, "sampledAt must not be null");
    }
}
