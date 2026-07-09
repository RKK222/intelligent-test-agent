package com.icbc.testagent.scheduler;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.dictionary.DictionaryRepository;
import com.icbc.testagent.domain.scheduler.ScheduledTask;
import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRegistrationStatus;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRepository;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRun;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunFilter;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.icbc.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.icbc.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 定时任务管理应用服务，封装任务定义查询、管理员调整和手动触发运行记录创建。
 */
@Service
public class SchedulerManagementService {

    private static final String DEFAULT_STOP_REASON = "管理员手工停止";

    private final ScheduledTaskRepository repository;
    private final CronScheduleCalculator cronScheduleCalculator;
    private final SchedulerProperties properties;
    private final ScheduledTaskDispatcher dispatcher;
    private final DictionaryRepository dictionaryRepository;
    private final Clock clock;

    /**
     * 注入持久化端口、Cron 校验器、scheduler 配置、后台 runner 唤醒端口、字典仓储和系统时钟。
     */
    public SchedulerManagementService(
            ScheduledTaskRepository repository,
            CronScheduleCalculator cronScheduleCalculator,
            SchedulerProperties properties,
            ScheduledTaskDispatcher dispatcher,
            DictionaryRepository dictionaryRepository,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.cronScheduleCalculator = Objects.requireNonNull(cronScheduleCalculator, "cronScheduleCalculator must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.dictionaryRepository = Objects.requireNonNull(dictionaryRepository, "dictionaryRepository must not be null");
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
        if (!properties.isEnabled()) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "定时任务后台扫描未启用，无法手动触发任务",
                    Map.of("taskKey", taskKey.value(), "schedulerEnabled", false));
        }
        repository.findActiveRunByTaskKey(taskKey).ifPresent(activeRun -> {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "同一 taskKey 已有未结束运行",
                    Map.of("taskKey", taskKey.value(), "activeTaskRunId", activeRun.taskRunId().value()));
        });
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

    /**
     * 管理员请求停止正在运行的任务；实际结束由 handler 协作式退出后由 runner 写入终态。
     */
    public ScheduledTaskRun stopRun(ScheduledTaskRunId taskRunId, UserId operatorUserId, String traceId) {
        ScheduledTaskRun current = getRun(taskRunId);
        if (current.status() != ScheduledTaskRunStatus.RUNNING) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "只有运行中的定时任务可以停止",
                    Map.of("taskRunId", taskRunId.value(), "status", current.status().name()));
        }
        return repository.saveRun(current.requestStop(operatorUserId, DEFAULT_STOP_REASON, clock.instant()));
    }

    public PageResponse<ScheduledTaskRun> findRuns(ScheduledTaskRunFilter filter, PageRequest pageRequest) {
        return repository.findRuns(filter == null ? ScheduledTaskRunFilter.empty() : filter, pageRequest);
    }

    public ScheduledTaskRun getRun(ScheduledTaskRunId taskRunId) {
        return repository.findRunById(taskRunId)
                .orElseThrow(() -> notFound("定时任务运行记录不存在", "taskRunId", taskRunId.value()));
    }

    public Optional<ScheduledTaskRun> findCurrentRunByTaskKey(ScheduledTaskKey taskKey) {
        return repository.findActiveRunByTaskKey(taskKey);
    }

    public Optional<ScheduledTaskRun> findLatestRunByTaskKey(ScheduledTaskKey taskKey) {
        return repository.findRuns(
                        new ScheduledTaskRunFilter(taskKey, null, null, null),
                        new PageRequest(1, 1))
                .items()
                .stream()
                .findFirst();
    }

    public String registrationStatusLabel(ScheduledTaskRegistrationStatus status) {
        return label(Dictionary.DICT_KEY_SCHEDULER_TASK_REGISTRATION_STATUS, status.name());
    }

    public String runStatusLabel(ScheduledTaskRunStatus status) {
        return label(Dictionary.DICT_KEY_SCHEDULER_RUN_STATUS, status.name());
    }

    public String triggerTypeLabel(ScheduledTaskTriggerType triggerType) {
        return label(Dictionary.DICT_KEY_SCHEDULER_TRIGGER_TYPE, triggerType.name());
    }

    private PlatformException notFound(String message, String key, String value) {
        return new PlatformException(ErrorCode.NOT_FOUND, message, Map.of(key, value));
    }

    private String label(String dictKey, String dictValue) {
        return dictionaryRepository.findByDictKeyAndValue(dictKey, dictValue)
                .map(Dictionary::dictLabel)
                .orElse(dictValue);
    }

    private String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
