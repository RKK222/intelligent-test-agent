package com.icbc.testagent.scheduler;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.scheduler.ScheduledTask;
import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRepository;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRun;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunFilter;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.icbc.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 定时任务管理应用服务，封装任务定义查询、管理员调整和手动触发运行记录创建。
 */
@Service
public class SchedulerManagementService {

    private final ScheduledTaskRepository repository;
    private final CronScheduleCalculator cronScheduleCalculator;
    private final ScheduledTaskDispatcher dispatcher;
    private final Clock clock;

    /**
     * 注入持久化端口、Cron 校验器、后台 runner 唤醒端口和系统时钟。
     */
    public SchedulerManagementService(
            ScheduledTaskRepository repository,
            CronScheduleCalculator cronScheduleCalculator,
            ScheduledTaskDispatcher dispatcher,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.cronScheduleCalculator = Objects.requireNonNull(cronScheduleCalculator, "cronScheduleCalculator must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public PageResponse<ScheduledTask> findTasks(PageRequest pageRequest) {
        return repository.findTasks(pageRequest);
    }

    public ScheduledTask getTask(ScheduledTaskKey taskKey) {
        return repository.findTaskByKey(taskKey)
                .orElseThrow(() -> notFound("定时任务不存在", "taskKey", taskKey.value()));
    }

    /**
     * 调整管理员可覆盖字段，并在 Cron 变化时重新计算下一次触发时间。
     */
    public ScheduledTask updateTask(ScheduledTaskKey taskKey, ScheduledTaskUpdateCommand command, String traceId) {
        ScheduledTask current = getTask(taskKey);
        Instant now = clock.instant();
        boolean enabled = command.enabled() == null ? current.enabled() : command.enabled();
        String cronExpression = textOrNull(command.cronExpression()) == null ? current.cronExpression() : command.cronExpression().trim();
        Duration lockTtl = command.lockTtl() == null ? current.lockTtl() : command.lockTtl();
        if (lockTtl.isZero() || lockTtl.isNegative()) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "锁 TTL 必须为正数",
                    Map.of("lockTtlSeconds", lockTtl.toSeconds()));
        }
        Instant nextFireAt = cronExpression.equals(current.cronExpression())
                ? current.nextFireAt()
                : cronScheduleCalculator.nextFireAt(cronExpression, now);
        ScheduledTask updated = current.withAdminSchedule(enabled, cronExpression, lockTtl, now)
                .withNextFireAt(nextFireAt, now);
        return repository.saveTask(new ScheduledTask(
                updated.taskKey(),
                updated.name(),
                updated.cronExpression(),
                updated.enabled(),
                updated.lockTtl(),
                updated.nextFireAt(),
                updated.registrationStatus(),
                updated.createdAt(),
                updated.updatedAt(),
                traceId));
    }

    /**
     * 创建管理员手动触发运行记录，实际执行由后台 runner 异步扫描。
     */
    public ScheduledTaskRun trigger(ScheduledTaskKey taskKey, UserId requestedByUserId, String traceId) {
        getTask(taskKey);
        Instant now = clock.instant();
        ScheduledTaskRun run = ScheduledTaskRun.pending(
                new ScheduledTaskRunId(RuntimeIdGenerator.scheduledTaskRunId()),
                taskKey,
                null,
                ScheduledTaskTriggerType.MANUAL,
                requestedByUserId,
                now,
                traceId);
        ScheduledTaskRun saved = repository.saveRun(run);
        dispatcher.wakeUp();
        return saved;
    }

    public PageResponse<ScheduledTaskRun> findRuns(ScheduledTaskRunFilter filter, PageRequest pageRequest) {
        return repository.findRuns(filter == null ? ScheduledTaskRunFilter.empty() : filter, pageRequest);
    }

    public ScheduledTaskRun getRun(ScheduledTaskRunId taskRunId) {
        return repository.findRunById(taskRunId)
                .orElseThrow(() -> notFound("定时任务运行记录不存在", "taskRunId", taskRunId.value()));
    }

    private PlatformException notFound(String message, String key, String value) {
        return new PlatformException(ErrorCode.NOT_FOUND, message, Map.of(key, value));
    }

    private String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
