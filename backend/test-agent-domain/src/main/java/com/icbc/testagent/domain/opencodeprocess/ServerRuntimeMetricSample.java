package com.icbc.testagent.domain.opencodeprocess;

import java.time.Instant;
import java.util.Objects;

/**
 * Redis 中按服务器身份保存的服务器级运行指标样本；不包含 JVM 进程私有指标。
 */
public record ServerRuntimeMetricSample(
        Instant sampledAt,
        Double cpuUsagePercent,
        Integer cpuCoreCount,
        Double loadAverage1m,
        Double loadAverage5m,
        Double loadAverage15m,
        Long memoryMaxBytes,
        Long memoryTotalBytes,
        Long memoryAvailableBytes,
        Long memoryFreeBytes,
        Long memoryUsedBytes,
        Double memoryUsagePercent,
        Long memoryBuffersBytes,
        Long memoryCachedBytes,
        Long swapTotalBytes,
        Long swapFreeBytes,
        Long swapUsedBytes,
        Double swapUsagePercent,
        Long diskMaxBytes,
        Long diskAvailableBytes,
        Long diskUsedBytes,
        Double diskUsagePercent) {

    public ServerRuntimeMetricSample {
        sampledAt = Objects.requireNonNull(sampledAt, "sampledAt must not be null");
    }

    /**
     * 兼容旧 Redis 样本和旧测试构造器；新增服务器字段按缺失处理。
     */
    public ServerRuntimeMetricSample(
            Instant sampledAt,
            Double cpuUsagePercent,
            Long memoryMaxBytes,
            Long memoryUsedBytes,
            Double memoryUsagePercent,
            Long diskMaxBytes,
            Long diskUsedBytes,
            Double diskUsagePercent) {
        this(
                sampledAt,
                cpuUsagePercent,
                null,
                null,
                null,
                null,
                memoryMaxBytes,
                memoryMaxBytes,
                null,
                null,
                memoryUsedBytes,
                memoryUsagePercent,
                null,
                null,
                null,
                null,
                null,
                null,
                diskMaxBytes,
                null,
                diskUsedBytes,
                diskUsagePercent);
    }
}
