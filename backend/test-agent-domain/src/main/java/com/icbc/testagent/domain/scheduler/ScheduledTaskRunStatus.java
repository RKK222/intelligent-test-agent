package com.icbc.testagent.domain.scheduler;

/**
 * 定时任务运行记录状态。
 */
public enum ScheduledTaskRunStatus {
    PENDING,
    RUNNING,
    STOPPING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    MANUALLY_STOPPED;

    /**
     * 框架用 active 状态判断同一个 taskKey 是否仍占用执行槽位。
     */
    public boolean active() {
        return this == PENDING || this == RUNNING || this == STOPPING;
    }
}
