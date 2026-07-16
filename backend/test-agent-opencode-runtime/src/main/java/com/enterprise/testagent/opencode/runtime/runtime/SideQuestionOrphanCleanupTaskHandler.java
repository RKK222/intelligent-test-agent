package com.enterprise.testagent.opencode.runtime.runtime;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 注册旁路孤儿清理任务，分布式互斥、运行记录和停止请求统一交给 scheduler 框架。 */
@Component
public class SideQuestionOrphanCleanupTaskHandler implements ScheduledTaskHandler {

    static final ScheduledTaskKey TASK_KEY =
            new ScheduledTaskKey("opencode-runtime.side-question-orphan-cleanup");
    static final String CRON = "0 */5 * * * *";
    static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final SideQuestionOrphanCleanupService cleanupService;

    /** 注入业务清理服务，handler 本身不实现锁或运行记录。 */
    public SideQuestionOrphanCleanupTaskHandler(SideQuestionOrphanCleanupService cleanupService) {
        this.cleanupService = Objects.requireNonNull(cleanupService, "cleanupService must not be null");
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "宠物旁路问答孤儿清理";
    }

    @Override
    public String cronExpression() {
        return CRON;
    }

    @Override
    public Duration lockTtl() {
        return LOCK_TTL;
    }

    /** 每轮把 scheduler 的协作式停止信号原样传给批量扫描。 */
    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        SideQuestionOrphanCleanupService.Result result = cleanupService.cleanup(
                context.traceId(),
                context::stopRequested);
        return ScheduledTaskResult.of(result.toMap());
    }
}
