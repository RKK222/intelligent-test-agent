package com.enterprise.testagent.scheduler;

import java.util.List;

/**
 * 任务是否可手工触发、可 Cron 执行及其阻塞原因。
 */
public record ScheduledTaskDiagnosis(
        boolean manualTriggerReady,
        boolean cronReady,
        List<SchedulerDiagnosticBlocker> blockers) {
}
