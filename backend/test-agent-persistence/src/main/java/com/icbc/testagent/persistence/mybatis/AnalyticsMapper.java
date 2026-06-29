package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 运营分析 MyBatis mapper，SQL 统一维护在 XML 中，避免新增 JDBC SQL。
 */
@Mapper
public interface AnalyticsMapper {

    List<AnalyticsActivityRow> loadRawActivityFacts(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);

    List<AnalyticsDurationFactRow> loadRunDurationSamples(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);

    void deleteHourly(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);

    int insertHourly(@Param("rows") List<AnalyticsActivityRow> rows, @Param("updatedAt") Instant updatedAt);

    List<AnalyticsActivityRow> loadHourly(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);

    void deleteDaily(
            @Param("startInclusive") LocalDate startInclusive,
            @Param("endInclusive") LocalDate endInclusive);

    int insertDaily(@Param("rows") List<AnalyticsActivityRow> rows, @Param("updatedAt") Instant updatedAt);

    void deleteDurationHistogram(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);

    int insertDurationHistogram(@Param("rows") List<AnalyticsDurationHistogramRow> rows, @Param("updatedAt") Instant updatedAt);

    AnalyticsFreshnessRow freshness(@Param("jobName") String jobName);

    int updateWatermark(
            @Param("jobName") String jobName,
            @Param("watermarkAt") Instant watermarkAt,
            @Param("generatedAt") Instant generatedAt,
            @Param("status") String status,
            @Param("message") String message,
            @Param("traceId") String traceId,
            @Param("updatedAt") Instant updatedAt);

    int insertWatermark(
            @Param("jobName") String jobName,
            @Param("watermarkAt") Instant watermarkAt,
            @Param("generatedAt") Instant generatedAt,
            @Param("status") String status,
            @Param("message") String message,
            @Param("traceId") String traceId,
            @Param("updatedAt") Instant updatedAt);

    int updateLock(
            @Param("lockName") String lockName,
            @Param("ownerId") String ownerId,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("now") Instant now);

    int insertLock(
            @Param("lockName") String lockName,
            @Param("ownerId") String ownerId,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("now") Instant now);

    int releaseLock(@Param("lockName") String lockName, @Param("ownerId") String ownerId);

    long countRegisteredUsers(
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userId") String userId);

    long countEnabledUsers(
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userId") String userId);

    List<AnalyticsOrganizationUserCountRow> organizationUserCounts(
            @Param("dimension") String dimension,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userId") String userId);

    List<AnalyticsActivityRow> queryHourlyRollups(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("model") String model,
            @Param("workspaceId") String workspaceId);

    List<AnalyticsActivityRow> queryDailyRollups(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("model") String model,
            @Param("workspaceId") String workspaceId);

    List<AnalyticsDurationHistogramRow> queryDurationHistogram(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("agentId") String agentId,
            @Param("model") String model,
            @Param("workspaceId") String workspaceId);

    List<AnalyticsFeedbackDetailRow> feedbackDetails(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("model") String model,
            @Param("workspaceId") String workspaceId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countFeedbackDetails(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("model") String model,
            @Param("workspaceId") String workspaceId);

    List<java.util.Map<String, Object>> negativeReasonCounts(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("model") String model,
            @Param("workspaceId") String workspaceId,
            @Param("limit") int limit);

    List<AnalyticsExceptionDetailRow> exceptionDetails(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("model") String model,
            @Param("workspaceId") String workspaceId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countExceptionDetails(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userId") String userId,
            @Param("agentId") String agentId,
            @Param("model") String model,
            @Param("workspaceId") String workspaceId);
}
