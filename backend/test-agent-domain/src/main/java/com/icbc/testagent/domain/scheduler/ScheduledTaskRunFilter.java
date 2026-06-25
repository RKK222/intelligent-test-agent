package com.icbc.testagent.domain.scheduler;

import com.icbc.testagent.domain.user.UserId;

/**
 * 管理 API 查询定时任务运行记录时使用的可选过滤条件。
 */
public record ScheduledTaskRunFilter(
        ScheduledTaskKey taskKey,
        ScheduledTaskRunStatus status,
        ScheduledTaskTriggerType triggerType,
        UserId requestedByUserId) {

    /**
     * 空过滤条件，用于查询所有运行记录。
     */
    public static ScheduledTaskRunFilter empty() {
        return new ScheduledTaskRunFilter(null, null, null, null);
    }
}
