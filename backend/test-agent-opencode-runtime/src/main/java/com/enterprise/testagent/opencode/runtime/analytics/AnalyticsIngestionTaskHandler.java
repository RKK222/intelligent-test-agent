package com.enterprise.testagent.opencode.runtime.analytics;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 每分钟消费运营 outbox；scheduler 负责集群互斥、运行审计和手工触发。 */
@Component
@ConditionalOnProperty(name = "test-agent.analytics.clickhouse.enabled", havingValue = "true")
public class AnalyticsIngestionTaskHandler implements ScheduledTaskHandler {

    static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("opencode-runtime.analytics-ingestion");
    static final String CRON = "0 * * * * *";
    static final Duration LOCK_TTL = Duration.ofMinutes(2);

    private final AnalyticsIngestionService service;

    public AnalyticsIngestionTaskHandler(AnalyticsIngestionService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "运营分析 ClickHouse 入库";
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
        return ScheduledTaskResult.of(service.ingestDue().toMap());
    }
}
