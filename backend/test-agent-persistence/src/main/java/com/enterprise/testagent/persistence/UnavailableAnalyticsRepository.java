package com.enterprise.testagent.persistence;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.analytics.AnalyticsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** ClickHouse 未启用时明确拒绝运营查询，禁止静默回退 PostgreSQL。 */
@Repository
@ConditionalOnProperty(name = "test-agent.analytics.clickhouse.enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableAnalyticsRepository implements AnalyticsRepository {

    @Override
    public List<AnalyticsModels.RawActivityRow> loadRawActivityFacts(Instant startInclusive, Instant endExclusive) {
        throw unavailable();
    }

    @Override
    public List<AnalyticsModels.DurationSample> loadRunDurationSamples(Instant startInclusive, Instant endExclusive) {
        throw unavailable();
    }

    @Override
    public void deleteHourly(Instant startInclusive, Instant endExclusive) {
        throw unavailable();
    }

    @Override
    public void insertHourly(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt) {
        throw unavailable();
    }

    @Override
    public List<AnalyticsModels.ActivityRollupRow> loadHourly(Instant startInclusive, Instant endExclusive) {
        throw unavailable();
    }

    @Override
    public void deleteDaily(LocalDate startInclusive, LocalDate endInclusive) {
        throw unavailable();
    }

    @Override
    public void insertDaily(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt) {
        throw unavailable();
    }

    @Override
    public void deleteDurationHistogram(Instant startInclusive, Instant endExclusive) {
        throw unavailable();
    }

    @Override
    public void insertDurationHistogram(List<AnalyticsModels.DurationHistogramRow> rows, Instant updatedAt) {
        throw unavailable();
    }

    @Override
    public Optional<AnalyticsModels.Freshness> freshness(String jobName, Instant staleThreshold) {
        throw unavailable();
    }

    @Override
    public void updateWatermark(
            String jobName,
            Instant watermarkAt,
            AnalyticsModels.FreshnessStatus status,
            String message,
            String traceId,
            Instant updatedAt) {
        throw unavailable();
    }

    @Override
    public boolean tryAcquireLock(String lockName, String ownerId, Instant lockedUntil, Instant now) {
        throw unavailable();
    }

    @Override
    public void releaseLock(String lockName, String ownerId) {
        throw unavailable();
    }

    @Override
    public long countRegisteredUsers(AnalyticsModels.Filter filter) {
        throw unavailable();
    }

    @Override
    public long countEnabledUsers(AnalyticsModels.Filter filter) {
        throw unavailable();
    }

    @Override
    public List<AnalyticsModels.ActivityRollupRow> queryRollups(AnalyticsModels.Filter filter) {
        throw unavailable();
    }

    @Override
    public long approximateP95DurationMs(AnalyticsModels.Filter filter) {
        throw unavailable();
    }

    @Override
    public PageResponse<AnalyticsModels.FeedbackDetail> feedbackDetails(AnalyticsModels.Filter filter) {
        throw unavailable();
    }

    @Override
    public Map<String, Long> negativeReasonCounts(AnalyticsModels.Filter filter) {
        throw unavailable();
    }

    @Override
    public PageResponse<AnalyticsModels.ExceptionDetail> exceptionDetails(AnalyticsModels.Filter filter) {
        throw unavailable();
    }

    @Override
    public List<AnalyticsModels.OrganizationUsageRow> organizationRows(
            AnalyticsModels.Filter filter,
            String dimension) {
        throw unavailable();
    }

    @Override
    public List<AnalyticsModels.CapabilityUsageRow> capabilityUsage(AnalyticsModels.Filter filter) {
        throw unavailable();
    }

    @Override
    public List<AnalyticsModels.FilterOption> organizations() {
        throw unavailable();
    }

    @Override
    public List<AnalyticsModels.FilterOption> rdDepartments(String organization) {
        throw unavailable();
    }

    @Override
    public List<AnalyticsModels.FilterOption> departments(String organization, String rdDepartment) {
        throw unavailable();
    }

    private static PlatformException unavailable() {
        return new PlatformException(
                ErrorCode.ANALYTICS_UNAVAILABLE,
                "ClickHouse 运营分析未启用");
    }
}
