package com.icbc.testagent.domain.scheduler;

/**
 * 定时任务运行触发来源。
 */
public enum ScheduledTaskTriggerType {
    CRON,
    MANUAL,
    USER_PLAN
}
