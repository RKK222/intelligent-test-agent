package com.enterprise.testagent.scheduler;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunRetentionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 清理定时任务已结束运行记录的框架维护任务。
 *
 * <p>清理边界由 {@code ended_at} 和当前时间倒推七天共同决定，未结束记录由持久层条件明确排除。</p>
 */
@Component
public class ScheduledTaskRunRetentionTaskHandler implements ScheduledTaskHandler {

    private static final int RETENTION_DAYS = 7;
    private static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("scheduler.run-retention-cleanup");
    private static final String CRON_EXPRESSION = "0 0 0 * * *";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final ScheduledTaskRunRetentionRepository repository;
    private final Clock clock;

    /**
     * 注入运行记录清理端口和可替换时钟，确保截止时间可在测试中稳定复现。
     */
    public ScheduledTaskRunRetentionTaskHandler(
            ScheduledTaskRunRetentionRepository repository,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "清理定时任务执行记录";
    }

    @Override
    public String cronExpression() {
        return CRON_EXPRESSION;
    }

    @Override
    public Duration lockTtl() {
        return LOCK_TTL;
    }

    /**
     * 按当前时钟计算七天保留边界；删除失败交由 XXL adapter 统一标记执行失败。
     */
    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        Instant cutoff = clock.instant().minus(RETENTION_DAYS, java.time.temporal.ChronoUnit.DAYS);
        int deletedCount = repository.deleteEndedBefore(cutoff);
        return ScheduledTaskResult.of(Map.of(
                "deletedCount", deletedCount,
                "retentionDays", RETENTION_DAYS,
                "cutoff", cutoff.toString()));
    }
}
