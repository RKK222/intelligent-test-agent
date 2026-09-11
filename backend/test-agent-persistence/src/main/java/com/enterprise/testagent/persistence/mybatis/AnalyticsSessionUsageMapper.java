package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户×会话发送次数统计 MyBatis mapper，SQL 统一维护在 XML 中。
 */
@Mapper
public interface AnalyticsSessionUsageMapper {

    List<AnalyticsSessionUsageRow> sessionMessageUsage(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userKeyword") String userKeyword,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countSessionMessageUsage(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userKeyword") String userKeyword);

    List<AnalyticsModels.SessionUsageSummaryRow> sessionMessageSummary(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userKeyword") String userKeyword,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countSessionMessageSummary(
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            @Param("organization") String organization,
            @Param("rdDepartment") String rdDepartment,
            @Param("department") String department,
            @Param("userKeyword") String userKeyword);
}
