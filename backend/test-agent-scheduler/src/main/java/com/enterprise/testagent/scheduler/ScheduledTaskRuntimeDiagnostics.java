package com.enterprise.testagent.scheduler;

import java.time.Instant;

/**
 * 单个任务的运行诊断信息，聚合任务定义、当前运行和 pending 手工运行数量。
 */
public record ScheduledTaskRuntimeDiagnostics(
        String taskKey,
        boolean enabled,
        String registrationStatus,
        String registrationStatusLabel,
        Instant nextFireAt,
        long lockTtlSeconds,
        ScheduledTaskRunDiagnosticSummary currentRun,
        ScheduledTaskRunDiagnosticSummary latestRun,
        long pendingManualRunCount) {
}
