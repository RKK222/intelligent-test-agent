package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.analytics.AnalyticsSessionUsageRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 用户×会话发送次数统计仓储：直接查询平台业务库，不受 ClickHouse 开关约束。
 */
@Repository
public class MyBatisAnalyticsSessionUsageRepository implements AnalyticsSessionUsageRepository {

    private final AnalyticsSessionUsageMapper mapper;

    public MyBatisAnalyticsSessionUsageRepository(AnalyticsSessionUsageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PageResponse<AnalyticsModels.SessionUsageRow> sessionMessageUsage(AnalyticsModels.Filter filter) {
        int pageSize = filter.pageSize();
        long offset = (long) Math.max(0, filter.page() - 1) * pageSize;
        List<AnalyticsModels.SessionUsageRow> items = mapper.sessionMessageUsage(
                        filter.startTime(),
                        filter.endTime(),
                        filter.organization(),
                        filter.rdDepartment(),
                        filter.department(),
                        filter.userKeyword(),
                        pageSize,
                        offset)
                .stream()
                .map(row -> new AnalyticsModels.SessionUsageRow(
                        row.userId(),
                        row.username(),
                        row.sessionId(),
                        row.sessionTitle(),
                        row.userMessageCount(),
                        row.firstMessageAt(),
                        row.lastMessageAt()))
                .toList();
        long total = mapper.countSessionMessageUsage(
                filter.startTime(),
                filter.endTime(),
                filter.organization(),
                filter.rdDepartment(),
                filter.department(),
                filter.userKeyword());
        return new PageResponse<>(items, filter.page(), pageSize, total);
    }
}
