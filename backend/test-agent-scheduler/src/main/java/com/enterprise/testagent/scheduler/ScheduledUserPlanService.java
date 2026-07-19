package com.enterprise.testagent.scheduler;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRepository;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRun;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** 业务模块创建一次性 USER_PLAN 运行记录的唯一入口。 */
@Service
public class ScheduledUserPlanService {
    private final ScheduledTaskRepository repository;
    private final ScheduledTaskRegistry registry;
    private final SchedulerProperties properties;
    private final ScheduledTaskDispatcher dispatcher;
    private final Clock clock;

    /** 注册表和 runner 延迟解析，避免 USER_PLAN handler 注册时反向形成启动依赖环。 */
    public ScheduledUserPlanService(
            ScheduledTaskRepository repository,
            @Lazy ScheduledTaskRegistry registry,
            SchedulerProperties properties,
            @Lazy ScheduledTaskDispatcher dispatcher,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.registry = Objects.requireNonNull(registry);
        this.properties = Objects.requireNonNull(properties);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.clock = Objects.requireNonNull(clock);
    }

    public ScheduledTaskRun schedule(
            ScheduledTaskKey taskKey,
            UserId requestedByUserId,
            Instant scheduledFireAt,
            String executionAffinity,
            String traceId) {
        requireAvailable(taskKey);
        if (executionAffinity == null || executionAffinity.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "USER_PLAN 执行亲和键不能为空");
        }
        ScheduledTaskRun run = ScheduledTaskRun.pending(
                new ScheduledTaskRunId(RuntimeIdGenerator.scheduledTaskRunId()),
                taskKey,
                null,
                ScheduledTaskTriggerType.USER_PLAN,
                requestedByUserId,
                scheduledFireAt,
                executionAffinity,
                traceId,
                clock.instant());
        ScheduledTaskRun saved = repository.saveRun(run);
        dispatcher.wakeUp();
        return saved;
    }

    public ScheduledTaskRun cancelPending(ScheduledTaskRunId taskRunId, String reason) {
        ScheduledTaskRun run = repository.findRunById(taskRunId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "定时任务运行记录不存在"));
        if (run.status() != ScheduledTaskRunStatus.PENDING || run.triggerType() != ScheduledTaskTriggerType.USER_PLAN) {
            throw new PlatformException(ErrorCode.CONFLICT, "用户计划运行已经开始", Map.of("status", run.status().name()));
        }
        ScheduledTaskRun skipped = run.skip(reason, clock.instant());
        if (repository.updateRunIfStatus(skipped, ScheduledTaskRunStatus.PENDING)) {
            return skipped;
        }
        ScheduledTaskRun latest = repository.findRunById(taskRunId).orElse(run);
        throw new PlatformException(
                ErrorCode.CONFLICT,
                "用户计划运行已经开始",
                Map.of("status", latest.status().name()));
    }

    /**
     * 系统补偿替换计划时尽力结束旧 PENDING 记录；已被认领或已终结时保持原状态。
     */
    public boolean cancelPendingIfPresent(ScheduledTaskRunId taskRunId, String reason) {
        if (taskRunId == null) return false;
        ScheduledTaskRun run = repository.findRunById(taskRunId).orElse(null);
        if (run == null
                || run.status() != ScheduledTaskRunStatus.PENDING
                || run.triggerType() != ScheduledTaskTriggerType.USER_PLAN) {
            return false;
        }
        return repository.updateRunIfStatus(run.skip(reason, clock.instant()), ScheduledTaskRunStatus.PENDING);
    }

    public boolean available() {
        return properties.isEnabled() && dispatcher.runnerRunning();
    }

    private void requireAvailable(ScheduledTaskKey taskKey) {
        if (!available()) {
            throw new PlatformException(ErrorCode.CONFLICT, "定时任务后台扫描未启用");
        }
        if (!registry.supports(taskKey, ScheduledTaskTriggerType.USER_PLAN)) {
            throw new PlatformException(ErrorCode.CONFLICT, "定时任务 handler 不支持 USER_PLAN");
        }
    }
}
