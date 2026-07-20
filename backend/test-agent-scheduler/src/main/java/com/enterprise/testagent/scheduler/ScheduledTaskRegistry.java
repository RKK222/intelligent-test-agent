package com.enterprise.testagent.scheduler;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.scheduler.ScheduledTask;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRegistrationStatus;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRepository;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 启动期任务注册表，扫描 handler Bean 并把代码注册任务同步到数据库。
 */
@Component
public class ScheduledTaskRegistry {

    private final ScheduledTaskRepository repository;
    private final CronScheduleCalculator cronScheduleCalculator;
    private final Clock clock;
    private final Map<ScheduledTaskKey, ScheduledTaskHandler> handlers;

    /**
     * 注入所有 handler Bean，并在构造阶段检查 taskKey 是否重复。
     */
    public ScheduledTaskRegistry(
            ScheduledTaskRepository repository,
            CronScheduleCalculator cronScheduleCalculator,
            Clock clock,
            List<ScheduledTaskHandler> handlers) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.cronScheduleCalculator = Objects.requireNonNull(cronScheduleCalculator, "cronScheduleCalculator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.handlers = indexHandlers(handlers == null ? List.of() : handlers);
    }

    /**
     * 同步代码注册任务。已有数据库定义保留管理员启停、Cron 和锁 TTL 覆盖值。
     */
    public void syncRegisteredTasks(String traceId) {
        syncHandlers(handlers.values(), traceId);
    }

    /**
     * XXL-JOB 接管周期/手工任务后，旧 PostgreSQL runner 只同步 USER_PLAN handler。
     */
    public void syncUserPlanTasks(String traceId) {
        syncHandlers(
                handlers.values().stream()
                        .filter(handler -> handler.supportedTriggerTypes().contains(ScheduledTaskTriggerType.USER_PLAN))
                        .toList(),
                traceId);
    }

    private void syncHandlers(java.util.Collection<ScheduledTaskHandler> selectedHandlers, String traceId) {
        Instant now = clock.instant();
        for (ScheduledTaskHandler handler : selectedHandlers) {
            ScheduledTaskKey taskKey = handler.taskKey();
            Optional<ScheduledTask> existing = repository.findTaskByKey(taskKey);
            if (existing.isEmpty()) {
                boolean cronSupported = handler.supportedTriggerTypes().contains(ScheduledTaskTriggerType.CRON);
                String cronExpression = cronSupported ? handler.cronExpression() : null;
                ScheduledTask task = ScheduledTask.registered(
                                taskKey,
                                handler.name(),
                                cronExpression,
                                handler.lockTtl(),
                                now,
                                traceId);
                if (cronSupported) {
                    task = task.withNextFireAt(cronScheduleCalculator.nextFireAt(cronExpression, now), now);
                }
                repository.saveTask(task);
                continue;
            }
            ScheduledTask current = existing.get();
            boolean cronSupported = handler.supportedTriggerTypes().contains(ScheduledTaskTriggerType.CRON);
            // 触发能力发生迁移时必须同步清理或恢复 Cron，避免 USER_PLAN-only 任务暴露管理员触发入口。
            String cronExpression = cronSupported
                    ? Objects.requireNonNullElse(current.cronExpression(), handler.cronExpression())
                    : null;
            Instant nextFireAt = cronSupported && current.nextFireAt() == null
                    ? cronScheduleCalculator.nextFireAt(cronExpression, now)
                    : cronSupported ? current.nextFireAt() : null;
            repository.saveTask(new ScheduledTask(
                    current.taskKey(),
                    handler.name(),
                    cronExpression,
                    current.enabled(),
                    current.lockTtl(),
                    nextFireAt,
                    ScheduledTaskRegistrationStatus.REGISTERED,
                    current.createdAt(),
                    now,
                    traceId));
        }
    }

    /**
     * 根据 taskKey 查找对应 handler。
     */
    public Optional<ScheduledTaskHandler> handlerFor(ScheduledTaskKey taskKey) {
        return Optional.ofNullable(handlers.get(taskKey));
    }

    /** 校验注册 handler 是否接受指定触发类型。 */
    public boolean supports(ScheduledTaskKey taskKey, ScheduledTaskTriggerType triggerType) {
        return handlerFor(taskKey).map(handler -> handler.supportedTriggerTypes().contains(triggerType)).orElse(false);
    }

    private Map<ScheduledTaskKey, ScheduledTaskHandler> indexHandlers(List<ScheduledTaskHandler> handlers) {
        Map<ScheduledTaskKey, ScheduledTaskHandler> indexed = new LinkedHashMap<>();
        for (ScheduledTaskHandler handler : handlers) {
            ScheduledTaskKey taskKey = handler.taskKey();
            ScheduledTaskHandler previous = indexed.putIfAbsent(taskKey, handler);
            if (previous != null) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "定时任务 taskKey 重复",
                        Map.of("taskKey", taskKey.value()));
            }
        }
        return Map.copyOf(indexed);
    }
}
