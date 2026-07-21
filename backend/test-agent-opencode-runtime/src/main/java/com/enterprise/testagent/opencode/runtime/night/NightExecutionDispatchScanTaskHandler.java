package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** XXL-JOB 每 15 分钟调用的夜间任务数据库扫描 handler。 */
@Component
public class NightExecutionDispatchScanTaskHandler implements ScheduledTaskHandler {

    public static final ScheduledTaskKey TASK_KEY =
            new ScheduledTaskKey("opencode-runtime.night-execution-dispatch");

    private final NightExecutionDispatchCoordinator coordinator;

    public NightExecutionDispatchScanTaskHandler(NightExecutionDispatchCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "夜间异步执行分发";
    }

    @Override
    public String cronExpression() {
        return "0 */15 * * * *";
    }

    @Override
    public Duration lockTtl() {
        return Duration.ofMinutes(15);
    }

    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        return ScheduledTaskResult.of(
                coordinator.dispatchDue(context.traceId(), context::stopRequested).toMap());
    }
}
