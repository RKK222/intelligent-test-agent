package com.enterprise.testagent.domain.analytics;

import java.time.Instant;
import java.util.List;

/** REDIS_SUMMARY 运营 stream 读取端口，候选 Run 必须来自 PostgreSQL 而非 Redis 全量扫描。 */
public interface AnalyticsRuntimeOutboxReader {

    record RunBatch(
            String runId,
            String lastStreamId,
            List<AnalyticsEventOutboxRepository.Event> events) {
    }

    List<RunBatch> read(int runLimit, int eventLimit, Instant recentTerminalThreshold);

    void acknowledge(List<RunBatch> batches, Instant acknowledgedAt);
}
