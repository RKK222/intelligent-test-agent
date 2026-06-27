package com.icbc.testagent.domain.opencodeprocess;

import java.time.Instant;
import java.util.Objects;

/**
 * Redis 中按时间排序保存的后端 Java 进程运行指标样本。
 */
public record BackendRuntimeMetricSample(
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

    public BackendRuntimeMetricSample {
        sampledAt = Objects.requireNonNull(sampledAt, "sampledAt must not be null");
    }
}
