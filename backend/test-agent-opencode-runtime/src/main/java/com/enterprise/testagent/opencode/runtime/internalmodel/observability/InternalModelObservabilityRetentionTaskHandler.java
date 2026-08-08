package com.enterprise.testagent.opencode.runtime.internalmodel.observability;

import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordRepository;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 按保留期清理内部模型调用观测数据：明细 30 天、小时聚合 180 天。 */
@Component
public class InternalModelObservabilityRetentionTaskHandler implements ScheduledTaskHandler {

    static final ScheduledTaskKey TASK_KEY =
            new ScheduledTaskKey("opencode-runtime.internal-model-observability-retention");
    static final String CRON = "0 30 3 * * *";
    static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final int RECORD_RETENTION_DAYS = 30;
    private static final int HOURLY_RETENTION_DAYS = 180;

    private final InternalModelCallRecordRepository repository;

    public InternalModelObservabilityRetentionTaskHandler(InternalModelCallRecordRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "内部模型调用观测数据清理";
    }

    @Override
    public String cronExpression() {
        return CRON;
    }

    @Override
    public Duration lockTtl() {
        return LOCK_TTL;
    }

    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        context.throwIfStopRequested();
        Instant now = Instant.now();
        int recordDeleted = repository.deleteRecordsBefore(now.minus(RECORD_RETENTION_DAYS, ChronoUnit.DAYS));
        context.throwIfStopRequested();
        int hourlyDeleted = repository.deleteHourlyStatsBefore(now.minus(HOURLY_RETENTION_DAYS, ChronoUnit.DAYS));
        return ScheduledTaskResult.of(Map.of(
                "recordsDeleted", recordDeleted,
                "hourlyStatsDeleted", hourlyDeleted,
                "recordRetentionDays", RECORD_RETENTION_DAYS,
                "hourlyRetentionDays", HOURLY_RETENTION_DAYS));
    }
}
