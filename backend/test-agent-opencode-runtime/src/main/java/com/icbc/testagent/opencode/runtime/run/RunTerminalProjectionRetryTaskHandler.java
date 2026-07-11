package com.icbc.testagent.opencode.runtime.run;

import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.scheduler.ScheduledTaskContext;
import com.icbc.testagent.scheduler.ScheduledTaskHandler;
import com.icbc.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 使用 scheduler 分布式锁周期唤醒 Redis 终态投影重试。 */
@Component
public class RunTerminalProjectionRetryTaskHandler implements ScheduledTaskHandler {

    static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("opencode-runtime.terminal-projection-retry");
    static final String CRON = "*/5 * * * * *";
    static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final RunTerminalProjectionRetryService retryService;

    /** 注入业务重试服务，锁、运行记录和手动触发仍由通用 scheduler 框架统一处理。 */
    public RunTerminalProjectionRetryTaskHandler(RunTerminalProjectionRetryService retryService) {
        this.retryService = Objects.requireNonNull(retryService, "retryService must not be null");
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "TestAgent Run 终态数据库重试";
    }

    @Override
    public String cronExpression() {
        return CRON;
    }

    @Override
    public Duration lockTtl() {
        return LOCK_TTL;
    }

    /** 执行 due 批次并只向 scheduler 运行记录返回低敏聚合计数。 */
    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        RunTerminalProjectionRetryService.Result result = retryService.retryDue(context::stopRequested);
        return ScheduledTaskResult.of(result.toMap());
    }
}
