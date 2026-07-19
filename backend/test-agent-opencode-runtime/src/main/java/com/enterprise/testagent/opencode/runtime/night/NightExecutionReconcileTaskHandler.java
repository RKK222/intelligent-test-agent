package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** 复用 scheduler 的 5 分钟周期收敛任务，不创建独立轮询线程。 */
@Component
public class NightExecutionReconcileTaskHandler implements ScheduledTaskHandler {

    public static final ScheduledTaskKey TASK_KEY =
            new ScheduledTaskKey("opencode-runtime.night-execution-reconcile");
    private final NightExecutionReconcileService reconcileService;

    public NightExecutionReconcileTaskHandler(NightExecutionReconcileService reconcileService) {
        this.reconcileService = reconcileService;
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "夜间异步执行收敛";
    }

    @Override
    public String cronExpression() {
        return "0 */5 * * * *";
    }

    @Override
    public Duration lockTtl() {
        return Duration.ofMinutes(5);
    }

    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        return ScheduledTaskResult.of(
                reconcileService.reconcile(context.traceId(), context::stopRequested).toMap());
    }
}
