package com.enterprise.testagent.scheduler;

import java.time.Instant;

/**
 * 当前 Java 进程内 scheduler 生效配置和扫描线程状态。
 */
public record SchedulerRuntimeDiagnostics(
        boolean enabled,
        boolean runnerRunning,
        String instanceId,
        long scanIntervalSeconds,
        int dueTaskLimit,
        int manualRunLimit,
        Instant lastScanStartedAt,
        Instant lastScanFinishedAt,
        String lastScanErrorMessage) {
}
