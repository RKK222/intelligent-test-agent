package com.enterprise.testagent.domain.analytics;

import java.time.Instant;
import java.util.List;

/** ClickHouse 事实写入端口；重复事件由 eventId/version 幂等收敛。 */
public interface AnalyticsEventSink {

    void append(List<AnalyticsEventOutboxRepository.Event> events, Instant ingestedAt);

    /** 回填门禁仅按稳定事件前缀核对，不暴露 ClickHouse 查询细节。 */
    default long countEventsByPrefix(String eventIdPrefix) {
        throw new UnsupportedOperationException("backfill verification is not supported");
    }

    default long countActivityFactsByPrefix(String eventIdPrefix) {
        throw new UnsupportedOperationException("backfill verification is not supported");
    }

    default long countUserDimensionFactsByPrefix(String eventIdPrefix) {
        throw new UnsupportedOperationException("backfill verification is not supported");
    }
}
