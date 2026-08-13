package com.enterprise.testagent.domain.analytics;

import java.time.Instant;
import java.util.List;

/** PostgreSQL 事务 outbox 端口，消费端只接触已经脱敏的运营事件。 */
public interface AnalyticsEventOutboxRepository {

    record Event(
            long id,
            String eventId,
            long eventVersion,
            String sourceType,
            String aggregateId,
            Instant occurredAt,
            String payloadJson) {
    }

    List<Event> pending(int limit, Instant now);

    void markPublished(List<Long> ids, Instant publishedAt);

    void markFailed(List<Long> ids, String error, Instant nextAttemptAt);
}
