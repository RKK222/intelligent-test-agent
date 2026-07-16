package com.enterprise.testagent.domain.analytics;

import com.enterprise.testagent.common.pagination.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 运营分析持久化端口。API 查询只读取 rollup 表，原始事实扫描仅供后台汇总任务使用。
 */
public interface AnalyticsRepository {

    List<AnalyticsModels.RawActivityRow> loadRawActivityFacts(Instant startInclusive, Instant endExclusive);

    List<AnalyticsModels.DurationSample> loadRunDurationSamples(Instant startInclusive, Instant endExclusive);

    void deleteHourly(Instant startInclusive, Instant endExclusive);

    void insertHourly(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt);

    List<AnalyticsModels.ActivityRollupRow> loadHourly(Instant startInclusive, Instant endExclusive);

    void deleteDaily(LocalDate startInclusive, LocalDate endInclusive);

    void insertDaily(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt);

    void deleteDurationHistogram(Instant startInclusive, Instant endExclusive);

    void insertDurationHistogram(List<AnalyticsModels.DurationHistogramRow> histogramRows, Instant updatedAt);

    Optional<AnalyticsModels.Freshness> freshness(String jobName, Instant staleThreshold);

    void updateWatermark(String jobName, Instant watermarkAt, AnalyticsModels.FreshnessStatus status, String message, String traceId, Instant updatedAt);

    boolean tryAcquireLock(String lockName, String ownerId, Instant lockedUntil, Instant now);

    void releaseLock(String lockName, String ownerId);

    long countRegisteredUsers(AnalyticsModels.Filter filter);

    long countEnabledUsers(AnalyticsModels.Filter filter);

    List<AnalyticsModels.ActivityRollupRow> queryRollups(AnalyticsModels.Filter filter);

    long approximateP95DurationMs(AnalyticsModels.Filter filter);

    PageResponse<AnalyticsModels.FeedbackDetail> feedbackDetails(AnalyticsModels.Filter filter);

    java.util.Map<String, Long> negativeReasonCounts(AnalyticsModels.Filter filter);

    PageResponse<AnalyticsModels.ExceptionDetail> exceptionDetails(AnalyticsModels.Filter filter);

    List<AnalyticsModels.OrganizationUsageRow> organizationRows(AnalyticsModels.Filter filter, String dimension);
}
