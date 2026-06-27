package com.icbc.testagent.domain.opencodeprocess;

/**
 * opencode-manager 心跳上报的容器资源指标；空资源字段表示当前环境无法采集。
 */
public record ContainerRuntimeMetrics(
        int portStart,
        int portEnd,
        int maxProcesses,
        int currentProcesses,
        String metricsSource,
        Double cpuUsagePercent,
        Long memoryMaxBytes,
        Long memoryUsedBytes,
        Double memoryUsagePercent,
        Double diskReadBytesPerSecond,
        Double diskWriteBytesPerSecond) {
}
