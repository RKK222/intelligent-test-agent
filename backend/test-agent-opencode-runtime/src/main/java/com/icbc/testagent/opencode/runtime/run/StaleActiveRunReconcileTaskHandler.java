package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.icbc.testagent.scheduler.ScheduledTaskContext;
import com.icbc.testagent.scheduler.ScheduledTaskHandler;
import com.icbc.testagent.scheduler.ScheduledTaskResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * opencode runtime 的业务定时任务注册器，复用 scheduler 框架的 Redis 锁和运行记录。
 */
@Component
public class StaleActiveRunReconcileTaskHandler implements ScheduledTaskHandler {

    static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("opencode-runtime.stale-active-run-reconcile");
    static final String CRON = "0 */5 * * * *";
    static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final StaleActiveRunReconcileService reconcileService;
    private final Instant startedAt;

    /**
     * 构造 handler 时记录 JVM 启动后 Bean 创建时间，用于跳过启动 catch-up。
     */
    @Autowired
    public StaleActiveRunReconcileTaskHandler(StaleActiveRunReconcileService reconcileService, Clock clock) {
        this.reconcileService = reconcileService;
        this.startedAt = (clock == null ? Clock.systemUTC() : clock).instant();
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "opencode 失联运行收敛";
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
     * 启动 catch-up 的 cron 运行不扫描，手动触发和 JVM 启动后的正常 cron 仍执行。
     */
    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        if (context.triggerType() == ScheduledTaskTriggerType.CRON
                && context.scheduledFireAt().isBefore(startedAt)) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("startupCatchUpSkipped", true);
            result.put("scheduledFireAt", context.scheduledFireAt().toString());
            result.put("startedAt", startedAt.toString());
            return ScheduledTaskResult.of(result);
        }
        StaleActiveRunReconcileService.Result result = reconcileService.reconcile(
                context.traceId(),
                context::stopRequested);
        return ScheduledTaskResult.of(result.toMap());
    }
}
