package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 夜间任务 USER_PLAN handler；不注册自身 Cron。 */
@Component
public class NightExecutionDispatchTaskHandler implements ScheduledTaskHandler {

    private final NightExecutionDispatchService dispatchService;

    public NightExecutionDispatchTaskHandler(NightExecutionDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return NightExecutionTaskApplicationService.TASK_KEY;
    }

    @Override
    public String name() {
        return "夜间异步执行";
    }

    @Override
    public String cronExpression() {
        return null;
    }

    @Override
    public Set<ScheduledTaskTriggerType> supportedTriggerTypes() {
        return Set.of(ScheduledTaskTriggerType.USER_PLAN);
    }

    @Override
    public Duration lockTtl() {
        return Duration.ofMinutes(20);
    }

    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        context.throwIfStopRequested();
        return dispatchService.dispatch(context);
    }
}
