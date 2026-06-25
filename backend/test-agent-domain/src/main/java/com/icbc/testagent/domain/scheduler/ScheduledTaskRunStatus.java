package com.icbc.testagent.domain.scheduler;

/**
 * 定时任务运行记录状态。
 */
public enum ScheduledTaskRunStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
