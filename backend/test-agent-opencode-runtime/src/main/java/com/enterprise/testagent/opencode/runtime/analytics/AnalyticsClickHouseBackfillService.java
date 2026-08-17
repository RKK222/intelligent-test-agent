package com.enterprise.testagent.opencode.runtime.analytics;

import com.enterprise.testagent.domain.analytics.AnalyticsBackfillSource;
import com.enterprise.testagent.domain.analytics.AnalyticsEventOutboxRepository;
import com.enterprise.testagent.domain.analytics.AnalyticsEventSink;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.analytics.AnalyticsRepository;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 一次性将 PostgreSQL 历史运营事实回填 ClickHouse，校验通过后才允许清理旧汇总表。 */
@Service
@ConditionalOnProperty(name = "test-agent.analytics.clickhouse.enabled", havingValue = "true")
public class AnalyticsClickHouseBackfillService {

    static final String CUTOVER_ID = "analytics-v1";
    static final String EVENT_PREFIX = "backfill-v1:";
    private static final String LOCK_NAME = "analytics-clickhouse-backfill";
    private static final int WRITE_BATCH_SIZE = 500;
    private static final ZoneId ANALYTICS_ZONE = ZoneId.of("Asia/Shanghai");

    private final AnalyticsBackfillSource source;
    private final AnalyticsEventSink sink;
    private final AnalyticsRepository locks;
    private final Clock clock;
    private final AnalyticsRollupApplicationService rollups;
    private final String ownerId;

    AnalyticsClickHouseBackfillService(
            AnalyticsBackfillSource source,
            AnalyticsEventSink sink,
            AnalyticsRepository locks,
            Clock clock) {
        this(source, sink, locks, clock, null);
    }

    @Autowired
    public AnalyticsClickHouseBackfillService(
            AnalyticsBackfillSource source,
            AnalyticsEventSink sink,
            AnalyticsRepository locks,
            Clock clock,
            AnalyticsRollupApplicationService rollups) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.locks = Objects.requireNonNull(locks, "locks must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.rollups = rollups;
        this.ownerId = ownerId();
    }

    public Result backfill(Instant startInclusive, Instant endExclusive, boolean cleanupLegacyRollups) {
        if (startInclusive == null || endExclusive == null || !startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("回填时间窗口非法");
        }
        Instant now = clock.instant();
        if (!locks.tryAcquireLock(LOCK_NAME, ownerId, now.plus(2, ChronoUnit.HOURS), now)) {
            throw new IllegalStateException("另一节点正在执行 ClickHouse 回填");
        }
        try {
            if (source.alreadyVerified(CUTOVER_ID)) {
                if (cleanupLegacyRollups) {
                    source.cleanupLegacyRollups();
                }
                return new Result(true, true, 0, 0, cleanupLegacyRollups);
            }
            long sourceCount = 0;
            List<AnalyticsEventOutboxRepository.Event> dimensionEvents = source.loadDimensionEvents();
            for (int offset = 0; offset < dimensionEvents.size(); offset += WRITE_BATCH_SIZE) {
                sink.append(dimensionEvents.subList(
                        offset, Math.min(offset + WRITE_BATCH_SIZE, dimensionEvents.size())), now);
            }
            sourceCount += dimensionEvents.size();
            Instant cursor = startInclusive;
            while (cursor.isBefore(endExclusive)) {
                Instant batchEnd = min(cursor.plus(1, ChronoUnit.DAYS), endExclusive);
                List<AnalyticsEventOutboxRepository.Event> events = source.loadEvents(cursor, batchEnd);
                for (int offset = 0; offset < events.size(); offset += WRITE_BATCH_SIZE) {
                    sink.append(events.subList(offset, Math.min(offset + WRITE_BATCH_SIZE, events.size())), now);
                }
                sourceCount += events.size();
                if (rollups != null) {
                    rollups.rebuildHourly(cursor, batchEnd, "analytics-clickhouse-backfill");
                }
                cursor = batchEnd;
            }
            if (rollups != null) {
                LocalDate date = startInclusive.atZone(ANALYTICS_ZONE).toLocalDate();
                LocalDate lastDate = endExclusive.minusNanos(1).atZone(ANALYTICS_ZONE).toLocalDate();
                while (!date.isAfter(lastDate)) {
                    rollups.rebuildDailyFromHourly(date, date, "analytics-clickhouse-backfill");
                    date = date.plusDays(1);
                }
            }
            long targetEvents = sink.countEventsByPrefix(EVENT_PREFIX);
            long targetFacts = sink.countActivityFactsByPrefix(EVENT_PREFIX)
                    + sink.countUserDimensionFactsByPrefix(EVENT_PREFIX);
            if (targetEvents != sourceCount || targetFacts != sourceCount) {
                throw new IllegalStateException("ClickHouse 回填校验失败: source=" + sourceCount
                        + ", events=" + targetEvents + ", facts=" + targetFacts);
            }
            source.markVerified(CUTOVER_ID, sourceCount, targetFacts, startInclusive, endExclusive, now);
            locks.updateWatermark(
                    AnalyticsRollupApplicationService.JOB_NAME,
                    endExclusive,
                    AnalyticsModels.FreshnessStatus.FRESH,
                    "ClickHouse 历史数据已回填",
                    "analytics-clickhouse-backfill",
                    now);
            if (cleanupLegacyRollups) {
                source.cleanupLegacyRollups();
            }
            return new Result(false, true, sourceCount, targetFacts, cleanupLegacyRollups);
        } finally {
            locks.releaseLock(LOCK_NAME, ownerId);
        }
    }

    private static Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static String ownerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
        } catch (Exception exception) {
            return "analytics-backfill-" + ProcessHandle.current().pid();
        }
    }

    public record Result(boolean skipped, boolean verified, long sourceEvents, long targetFacts, boolean cleaned) {
    }
}
