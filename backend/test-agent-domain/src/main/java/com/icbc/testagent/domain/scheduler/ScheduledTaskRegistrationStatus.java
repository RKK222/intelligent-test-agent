package com.icbc.testagent.domain.scheduler;

/**
 * 定时任务定义与当前代码 handler 的注册关系。
 */
public enum ScheduledTaskRegistrationStatus {
    REGISTERED,
    MISSING_HANDLER
}
