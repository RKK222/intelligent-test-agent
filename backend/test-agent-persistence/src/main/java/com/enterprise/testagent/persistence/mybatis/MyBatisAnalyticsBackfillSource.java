package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.analytics.AnalyticsBackfillSource;
import com.enterprise.testagent.domain.analytics.AnalyticsEventOutboxRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 从业务事实生成幂等脱敏回填事件，不读取 PostgreSQL 旧汇总表。 */
@Repository
@ConditionalOnProperty(name = "test-agent.analytics.clickhouse.enabled", havingValue = "true")
public class MyBatisAnalyticsBackfillSource implements AnalyticsBackfillSource {

    private final AnalyticsEventOutboxMapper outboxMapper;

    public MyBatisAnalyticsBackfillSource(AnalyticsEventOutboxMapper outboxMapper) {
        this.outboxMapper = Objects.requireNonNull(outboxMapper, "outboxMapper must not be null");
    }

    @Override
    public List<AnalyticsEventOutboxRepository.Event> loadDimensionEvents() {
        return events(outboxMapper.backfillUserDimensionEvents());
    }

    @Override
    public List<AnalyticsEventOutboxRepository.Event> loadEvents(Instant startInclusive, Instant endExclusive) {
        return events(outboxMapper.backfillDetailEvents(startInclusive, endExclusive));
    }

    private List<AnalyticsEventOutboxRepository.Event> events(List<AnalyticsBackfillEventRow> rows) {
        return rows.stream()
                .map(row -> new AnalyticsEventOutboxRepository.Event(
                        0, row.eventId(), row.eventVersion(), row.sourceType(), row.aggregateId(),
                        row.occurredAt(), row.payloadJson()))
                .sorted(Comparator.comparing(AnalyticsEventOutboxRepository.Event::eventId))
                .toList();
    }

    @Override
    public boolean alreadyVerified(String cutoverId) {
        return "VERIFIED".equals(outboxMapper.cutoverStatus(cutoverId));
    }

    @Override
    @Transactional
    public void markVerified(
            String cutoverId,
            long sourceCount,
            long targetCount,
            Instant coverageStart,
            Instant coverageEnd,
            Instant verifiedAt) {
        outboxMapper.upsertVerifiedCutover(
                cutoverId, sourceCount, targetCount, coverageStart, coverageEnd, verifiedAt);
    }

    @Override
    @Transactional
    public void cleanupLegacyRollups() {
        outboxMapper.cleanupLegacyRollups();
    }

}
