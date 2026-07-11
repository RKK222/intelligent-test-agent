package com.icbc.testagent.opencode.runtime.run;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 隔离 owner lease 续租与可能阻塞的恢复/取消/数据库重试，避免维护任务互相饿死。 */
@Configuration(proxyBeanMethods = false)
public class RunRuntimeSchedulingConfiguration {

    public static final String OWNER_LEASE_SCHEDULER = "runOwnerLeaseTaskScheduler";
    public static final String MAINTENANCE_SCHEDULER = "runMaintenanceTaskScheduler";

    /** 单独线程只负责 5 秒 owner lease 续租，禁止运行远端 I/O 或数据库事务。 */
    @Bean(name = OWNER_LEASE_SCHEDULER)
    ThreadPoolTaskScheduler runOwnerLeaseTaskScheduler() {
        return scheduler(1, "run-owner-lease-");
    }

    /** 恢复、到期收敛和终态重试使用独立线程池，不占用 Boot 默认调度线程。 */
    @Bean(name = MAINTENANCE_SCHEDULER)
    ThreadPoolTaskScheduler runMaintenanceTaskScheduler() {
        return scheduler(4, "run-maintenance-");
    }

    private ThreadPoolTaskScheduler scheduler(int poolSize, String prefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(prefix);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
