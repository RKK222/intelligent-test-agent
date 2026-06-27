package com.icbc.testagent.domain.opencodeprocess;

import java.time.Instant;
import java.util.Objects;

/**
 * Redis 中按服务器身份保存的服务器级运行指标样本；不包含 JVM 进程私有指标。
 */
public record ServerRuntimeMetricSample(
        Instant sampledAt,
        Double cpuUsagePercent,
        Long memoryMaxBytes,
        Long memoryUsedBytes,
        Double memoryUsagePercent,
        Long diskMaxBytes,
        Long diskUsedBytes,
        Double diskUsagePercent) {

    public ServerRuntimeMetricSample {
        sampledAt = Objects.requireNonNull(sampledAt, "sampledAt must not be null");
    }
}
