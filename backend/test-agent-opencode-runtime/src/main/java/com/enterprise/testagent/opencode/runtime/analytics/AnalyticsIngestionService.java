package com.enterprise.testagent.opencode.runtime.analytics;

import com.enterprise.testagent.domain.analytics.AnalyticsEventOutboxRepository;
import com.enterprise.testagent.domain.analytics.AnalyticsEventSink;
import com.enterprise.testagent.domain.analytics.AnalyticsRuntimeOutboxReader;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;

/** 将 PostgreSQL 脱敏 outbox 幂等投递到 ClickHouse，目标端故障时不反向影响业务写入。 */
@Service
@ConditionalOnProperty(name = "test-agent.analytics.clickhouse.enabled", havingValue = "true")
public class AnalyticsIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsIngestionService.class);
    private static final int BATCH_SIZE = 500;

    private final AnalyticsEventOutboxRepository outbox;
    private final AnalyticsEventSink sink;
    private final Clock clock;
    private final AnalyticsRuntimeOutboxReader runtimeOutbox;

    public AnalyticsIngestionService(
            AnalyticsEventOutboxRepository outbox,
            AnalyticsEventSink sink,
            Clock clock) {
        this(outbox, sink, new NoopRuntimeOutboxReader(), clock);
    }

    @Autowired
    public AnalyticsIngestionService(
            AnalyticsEventOutboxRepository outbox,
            AnalyticsEventSink sink,
            AnalyticsRuntimeOutboxReader runtimeOutbox,
            Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox must not be null");
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.runtimeOutbox = Objects.requireNonNull(runtimeOutbox, "runtimeOutbox must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** 单轮最多消费 500 条，失败统一退避一分钟并仅记录异常类型。 */
    public Result ingestDue() {
        Instant now = clock.instant();
        List<AnalyticsEventOutboxRepository.Event> events = outbox.pending(BATCH_SIZE, now);
        List<AnalyticsRuntimeOutboxReader.RunBatch> runtimeBatches = runtimeOutbox.read(
                200, BATCH_SIZE, now.minusSeconds(14 * 86_400L));
        List<AnalyticsEventOutboxRepository.Event> runtimeEvents = runtimeBatches.stream()
                .flatMap(batch -> batch.events().stream())
                .toList();
        if (events.isEmpty() && runtimeEvents.isEmpty()) {
            return new Result(0, 0, 0);
        }
        List<Long> ids = events.stream().map(AnalyticsEventOutboxRepository.Event::id).toList();
        try {
            List<AnalyticsEventOutboxRepository.Event> allEvents = new java.util.ArrayList<>(events);
            allEvents.addAll(runtimeEvents);
            sink.append(allEvents, now);
            if (!ids.isEmpty()) {
                outbox.markPublished(ids, now);
            }
            runtimeOutbox.acknowledge(runtimeBatches, now);
            return new Result(allEvents.size(), allEvents.size(), 0);
        } catch (RuntimeException exception) {
            String errorType = exception.getClass().getSimpleName();
            if (!ids.isEmpty()) {
                outbox.markFailed(ids, errorType, now.plusSeconds(60));
            }
            LOGGER.warn("运营 outbox 写入 ClickHouse 失败，将在一分钟后重试: count={}, errorType={}",
                    events.size(), errorType);
            int failed = events.size() + runtimeEvents.size();
            return new Result(failed, 0, failed);
        }
    }

    public record Result(int scanned, int published, int failed) {

        public Map<String, Object> toMap() {
            return Map.of("scanned", scanned, "published", published, "failed", failed);
        }
    }

    private static final class NoopRuntimeOutboxReader implements AnalyticsRuntimeOutboxReader {
        @Override
        public List<RunBatch> read(int runLimit, int eventLimit, Instant recentTerminalThreshold) {
            return List.of();
        }

        @Override
        public void acknowledge(List<RunBatch> batches, Instant acknowledgedAt) {
        }
    }
}
