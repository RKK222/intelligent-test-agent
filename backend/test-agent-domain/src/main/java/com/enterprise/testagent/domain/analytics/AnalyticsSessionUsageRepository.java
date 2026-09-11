package com.enterprise.testagent.domain.analytics;

import com.enterprise.testagent.common.pagination.PageResponse;

/**
 * 用户×会话发送次数统计的持久化端口。
 *
 * <p>该口径依赖平台业务库的存储模式（LEGACY_FULL / REDIS_SUMMARY）、来源类型
 * （MANUAL / SCHEDULED_TASK / SIDE_QUESTION）与跨表人员归属链，ClickHouse 运营事实表
 * 未采集这些字段，因此独立于 {@link AnalyticsRepository} 与 ClickHouse 开关，直接读业务库。
 */
public interface AnalyticsSessionUsageRepository {

    PageResponse<AnalyticsModels.SessionUsageRow> sessionMessageUsage(AnalyticsModels.Filter filter);
}
