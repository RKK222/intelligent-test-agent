package com.enterprise.testagent.app.config;

import com.enterprise.testagent.opencode.runtime.analytics.AnalyticsClickHouseBackfillService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 显式开关启动的一次性 ClickHouse 回填；失败时中止节点就绪，不开启分析查询。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(
        name = {
            "test-agent.analytics.clickhouse.enabled",
            "test-agent.analytics.clickhouse.backfill.enabled"
        },
        havingValue = "true")
public class AnalyticsClickHouseBackfillRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsClickHouseBackfillRunner.class);

    private final AnalyticsClickHouseBackfillService service;
    private final Instant start;
    private final String end;
    private final boolean cleanupLegacyRollups;

    public AnalyticsClickHouseBackfillRunner(
            AnalyticsClickHouseBackfillService service,
            @Value("${test-agent.analytics.clickhouse.backfill.start}") String start,
            @Value("${test-agent.analytics.clickhouse.backfill.end:}") String end,
            @Value("${test-agent.analytics.clickhouse.backfill.cleanup-legacy-rollups:false}")
            boolean cleanupLegacyRollups) {
        this.service = service;
        this.start = Instant.parse(start);
        this.end = end;
        this.cleanupLegacyRollups = cleanupLegacyRollups;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant endExclusive = end == null || end.isBlank() ? Instant.now() : Instant.parse(end);
        AnalyticsClickHouseBackfillService.Result result = service.backfill(
                start, endExclusive, cleanupLegacyRollups);
        LOGGER.info(
                "ClickHouse 运营回填完成, skipped={}, verified={}, sourceEvents={}, targetFacts={}, cleaned={}",
                result.skipped(), result.verified(), result.sourceEvents(), result.targetFacts(), result.cleaned());
    }
}
