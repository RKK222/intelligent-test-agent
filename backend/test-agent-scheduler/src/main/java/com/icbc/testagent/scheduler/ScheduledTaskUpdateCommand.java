package com.icbc.testagent.scheduler;

import java.time.Duration;

/**
 * 管理端调整任务定义的命令对象，null 字段表示保持原值。
 */
public record ScheduledTaskUpdateCommand(Boolean enabled, String cronExpression, Duration lockTtl) {
}
