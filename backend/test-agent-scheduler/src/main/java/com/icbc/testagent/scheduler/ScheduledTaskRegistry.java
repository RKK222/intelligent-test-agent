package com.icbc.testagent.scheduler;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.scheduler.ScheduledTask;
import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRegistrationStatus;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRepository;
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
        Instant now = clock.instant();
        for (ScheduledTaskHandler handler : handlers.values()) {
            ScheduledTaskKey taskKey = handler.taskKey();
            Optional<ScheduledTask> existing = repository.findTaskByKey(taskKey);
            if (existing.isEmpty()) {
                ScheduledTask task = ScheduledTask.registered(
                                taskKey,
                                handler.name(),
                                handler.cronExpression(),
                                handler.lockTtl(),
                                now,
                                traceId)
                        .withNextFireAt(cronScheduleCalculator.nextFireAt(handler.cronExpression(), now), now);
                repository.saveTask(task);
                continue;
            }
            ScheduledTask current = existing.get();
            Instant nextFireAt = current.nextFireAt() == null
                    ? cronScheduleCalculator.nextFireAt(current.cronExpression(), now)
                    : current.nextFireAt();
            repository.saveTask(new ScheduledTask(
                    current.taskKey(),
                    handler.name(),
                    current.cronExpression(),
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
