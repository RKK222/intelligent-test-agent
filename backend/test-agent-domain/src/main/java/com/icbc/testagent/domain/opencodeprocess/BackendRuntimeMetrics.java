package com.icbc.testagent.domain.opencodeprocess;

/**
 * 后端 Java 进程心跳上报的服务器与 JVM 资源指标；空字段表示运行环境暂不可采集。
 */
public record BackendRuntimeMetrics(
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
}
