package com.enterprise.testagent.domain.analytics;

import java.time.Instant;
import java.util.List;

/** PostgreSQL 历史运营事实回填端口，仅返回脱敏运营事件。 */
public interface AnalyticsBackfillSource {

    /** 用户维度是运营查询的完整基数，不受行为事实回填时间窗限制。 */
    List<AnalyticsEventOutboxRepository.Event> loadDimensionEvents();

    List<AnalyticsEventOutboxRepository.Event> loadEvents(Instant startInclusive, Instant endExclusive);

    boolean alreadyVerified(String cutoverId);

    void markVerified(
            String cutoverId,
            long sourceCount,
            long targetCount,
            Instant coverageStart,
            Instant coverageEnd,
            Instant verifiedAt);

    void cleanupLegacyRollups();
}
