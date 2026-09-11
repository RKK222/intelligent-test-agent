package com.enterprise.testagent.opencode.runtime.analytics;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.analytics.AnalyticsSessionUsageRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 用户×会话发送次数统计查询服务。
 *
 * <p>该统计口径依赖平台业务库的存储模式、来源类型与人员归属，独立于只读 ClickHouse 的
 * {@link AnalyticsQueryService}；单独成类以隔离业务库例外，并避免改动其构造器依赖。
 */
@Service
public class AnalyticsSessionUsageQueryService {

    private final AnalyticsSessionUsageRepository repository;

    public AnalyticsSessionUsageQueryService(AnalyticsSessionUsageRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public PageResponse<AnalyticsModels.SessionUsageRow> sessionMessageUsage(AnalyticsModels.Filter filter) {
        return repository.sessionMessageUsage(filter);
    }

    public PageResponse<AnalyticsModels.SessionUsageSummaryRow> sessionMessageSummary(AnalyticsModels.Filter filter) {
        return repository.sessionMessageSummary(filter);
    }
}
