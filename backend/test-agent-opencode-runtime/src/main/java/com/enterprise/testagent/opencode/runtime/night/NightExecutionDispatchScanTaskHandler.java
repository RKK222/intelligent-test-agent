package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.opencode.runtime.run.RunResendDispatchCoordinator;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 复用已注册的 XXL 每分钟任务，同时扫描夜间执行与到期重发，避免新增未注册 task key。 */
@Component
public class NightExecutionDispatchScanTaskHandler implements ScheduledTaskHandler {

    public static final ScheduledTaskKey TASK_KEY =
            new ScheduledTaskKey("opencode-runtime.night-execution-dispatch");

    private final NightExecutionDispatchCoordinator coordinator;
    private final RunResendDispatchCoordinator resendCoordinator;

    public NightExecutionDispatchScanTaskHandler(
            NightExecutionDispatchCoordinator coordinator,
            RunResendDispatchCoordinator resendCoordinator) {
        this.coordinator = coordinator;
        this.resendCoordinator = resendCoordinator;
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
        return "0 * * * * *";
    }

    @Override
    public Duration lockTtl() {
        return Duration.ofMinutes(15);
    }

    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        Map<String, Object> result = new LinkedHashMap<>(
                coordinator.dispatchDue(context.traceId(), context::stopRequested).toMap());
        // 重发沿用同一停止信号和全局调度锁；字段使用 resend 前缀，不覆盖夜间分发指标。
        result.putAll(resendCoordinator.dispatchDue(
                context.traceId(), context::stopRequested).toMap());
        return ScheduledTaskResult.of(result);
    }
}
