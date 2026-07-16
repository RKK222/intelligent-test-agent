package com.enterprise.testagent.opencode.runtime.analytics;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 将运营分析汇总注册到统一定时任务框架，由框架负责计划、互斥和运行审计。
 */
@Component
public class AnalyticsRollupTaskHandler implements ScheduledTaskHandler {

    static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("opencode-runtime.analytics-rollup");
    static final String CRON = "0 */5 * * * *";
    static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final AnalyticsRollupApplicationService service;

    public AnalyticsRollupTaskHandler(AnalyticsRollupApplicationService service) {
        this.service = service;
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "TestAgent 运营分析汇总";
    }

    @Override
    public String cronExpression() {
        return CRON;
    }

    @Override
    public Duration lockTtl() {
        return LOCK_TTL;
    }

    /**
     * 在业务阶段前后检查动态停止信号，让 runner 统一记录人工停止终态。
     */
    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        context.throwIfStopRequested();
        AnalyticsRollupApplicationService.Result result = service.rollupRecent(
                context.traceId(),
                context::stopRequested);
        context.throwIfStopRequested();
        return ScheduledTaskResult.of(result.toMap());
    }
}
