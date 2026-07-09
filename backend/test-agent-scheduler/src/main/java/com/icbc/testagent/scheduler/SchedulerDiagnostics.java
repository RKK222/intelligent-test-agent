package com.icbc.testagent.scheduler;

/**
 * 定时任务管理页使用的只读诊断总览。
 */
public record SchedulerDiagnostics(
        SchedulerRuntimeDiagnostics scheduler,
        ScheduledTaskLockInspection redisLock,
        ScheduledTaskRuntimeDiagnostics task,
        ScheduledTaskDiagnosis diagnosis) {
}
