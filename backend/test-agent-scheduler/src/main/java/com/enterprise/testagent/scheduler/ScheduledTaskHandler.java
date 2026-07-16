package com.enterprise.testagent.scheduler;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import java.time.Duration;

/**
 * 定时任务处理器契约。业务模块只需要提供 Bean，本框架统一负责注册、互斥、调度和运行记录。
 */
public interface ScheduledTaskHandler {

    /**
     * 返回全局稳定任务 key，用于数据库定义、Redis 锁和运行记录关联。
     */
    ScheduledTaskKey taskKey();

    /**
     * 返回面向管理员展示的任务名称。
     */
    String name();

    /**
     * 返回代码默认 Cron 表达式；管理员覆盖后数据库值优先生效。
     */
    String cronExpression();

    /**
     * 返回代码默认锁租约时长；管理员覆盖后数据库值优先生效。
     */
    default Duration lockTtl() {
        return Duration.ofMinutes(5);
    }

    /**
     * 执行一次任务。实现方不得自行写运行记录，也不得自行实现分布式互斥。
     */
    ScheduledTaskResult run(ScheduledTaskContext context);
}
