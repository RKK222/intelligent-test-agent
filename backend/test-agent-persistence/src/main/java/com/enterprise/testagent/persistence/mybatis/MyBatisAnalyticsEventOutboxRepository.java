package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.analytics.AnalyticsEventOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/** 使用 PostgreSQL 检查点可靠投递脱敏运营事件。 */
@Repository
public class MyBatisAnalyticsEventOutboxRepository implements AnalyticsEventOutboxRepository {

    private final AnalyticsEventOutboxMapper mapper;

    public MyBatisAnalyticsEventOutboxRepository(AnalyticsEventOutboxMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public List<Event> pending(int limit, Instant now) {
        return mapper.pending(limit, now);
    }

    @Override
    public void markPublished(List<Long> ids, Instant publishedAt) {
        if (!ids.isEmpty()) {
            mapper.markPublished(ids, publishedAt);
        }
    }

    @Override
    public void markFailed(List<Long> ids, String error, Instant nextAttemptAt) {
        if (!ids.isEmpty()) {
            mapper.markFailed(ids, truncate(error), nextAttemptAt);
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }
}
