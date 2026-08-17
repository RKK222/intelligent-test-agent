package com.enterprise.testagent.persistence.clickhouse;

import com.enterprise.testagent.domain.analytics.AnalyticsEventOutboxRepository;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.persistence.mybatis.AnalyticsActivityRow;
import com.enterprise.testagent.persistence.mybatis.AnalyticsExceptionDetailRow;
import com.enterprise.testagent.persistence.mybatis.AnalyticsFeedbackDetailRow;
import com.enterprise.testagent.persistence.mybatis.AnalyticsFreshnessRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** ClickHouse 运营事实、汇总和查询 mapper。 */
public interface ClickHouseAnalyticsMapper {

    int insertEvents(
            @Param("events") List<AnalyticsEventOutboxRepository.Event> events,
            @Param("ingestedAt") Instant ingestedAt);

    long countEventsByPrefix(@Param("eventIdPrefix") String eventIdPrefix);

    long countActivityFactsByPrefix(@Param("eventIdPrefix") String eventIdPrefix);

    long countUserDimensionFactsByPrefix(@Param("eventIdPrefix") String eventIdPrefix);

    List<AnalyticsActivityRow> loadRawActivityFacts(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);

    int insertHourly(@Param("rows") List<AnalyticsActivityRow> rows, @Param("updatedAt") Instant updatedAt);

    int insertDaily(@Param("rows") List<AnalyticsActivityRow> rows, @Param("updatedAt") Instant updatedAt);

    List<AnalyticsActivityRow> loadHourly(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);

    List<AnalyticsActivityRow> queryHourlyRollups(
            @Param("filter") AnalyticsModels.Filter filter);

    List<AnalyticsActivityRow> queryDailyRollups(
            @Param("filter") AnalyticsModels.Filter filter,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    AnalyticsFreshnessRow freshness(@Param("jobName") String jobName);

    int insertWatermark(
            @Param("jobName") String jobName,
            @Param("watermarkAt") Instant watermarkAt,
            @Param("generatedAt") Instant generatedAt,
            @Param("status") String status,
            @Param("message") String message,
            @Param("coverageStart") Instant coverageStart,
            @Param("coverageEnd") Instant coverageEnd,
            @Param("attributionMode") String attributionMode,
            @Param("version") long version);

    long countEventUsers(@Param("filter") AnalyticsModels.Filter filter);

    long countRegisteredUsers(@Param("filter") AnalyticsModels.Filter filter);

    long countEnabledUsers(@Param("filter") AnalyticsModels.Filter filter);

    Long p95DurationMs(@Param("filter") AnalyticsModels.Filter filter);

    List<AnalyticsFeedbackDetailRow> feedbackDetails(
            @Param("filter") AnalyticsModels.Filter filter,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countFeedbackDetails(@Param("filter") AnalyticsModels.Filter filter);

    List<Map<String, Object>> negativeReasonCounts(
            @Param("filter") AnalyticsModels.Filter filter,
            @Param("limit") int limit);

    List<AnalyticsExceptionDetailRow> exceptionDetails(
            @Param("filter") AnalyticsModels.Filter filter,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countExceptionDetails(@Param("filter") AnalyticsModels.Filter filter);

    List<AnalyticsModels.CapabilityUsageRow> capabilityUsage(@Param("filter") AnalyticsModels.Filter filter);

    List<com.enterprise.testagent.persistence.mybatis.AnalyticsOrganizationUserCountRow> organizationUserCounts(
            @Param("dimension") String dimension,
            @Param("filter") AnalyticsModels.Filter filter);

    List<AnalyticsModels.FilterOption> organizations();

    List<AnalyticsModels.FilterOption> rdDepartments(@Param("organization") String organization);

    List<AnalyticsModels.FilterOption> departments(
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment);
}
