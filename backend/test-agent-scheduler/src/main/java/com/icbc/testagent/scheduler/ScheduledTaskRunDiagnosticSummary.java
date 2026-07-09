package com.icbc.testagent.scheduler;

import java.time.Instant;

/**
 * 诊断视图使用的运行记录摘要，避免把领域对象直接暴露给 API 层。
 */
public record ScheduledTaskRunDiagnosticSummary(
        String taskRunId,
        String status,
        String statusLabel,
        String triggerType,
        String triggerTypeLabel,
        String requestedByUserId,
        Instant scheduledFireAt,
        Instant startedAt,
        Instant endedAt,
        String ownerInstanceId) {
}
