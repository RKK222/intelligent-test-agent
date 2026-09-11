package com.enterprise.testagent.opencode.runtime.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.analytics.AnalyticsSessionUsageRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsSessionUsageQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T04:00:00Z");

    @Test
    void delegatesToRepositoryAndReturnsPage() {
        AnalyticsModels.SessionUsageRow row = new AnalyticsModels.SessionUsageRow(
                "usr_1", "张三", "ses_1", "会话标题", 3, NOW, NOW.plusSeconds(60));
        AnalyticsSessionUsageQueryService service = new AnalyticsSessionUsageQueryService(
                new FakeSessionUsageRepository(
                        new PageResponse<>(List.of(row), 1, 20, 1),
                        new PageResponse<>(List.of(), 1, 20, 0)));

        PageResponse<AnalyticsModels.SessionUsageRow> page = service.sessionMessageUsage(filter());

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).userMessageCount()).isEqualTo(3);
        assertThat(page.items().get(0).sessionTitle()).isEqualTo("会话标题");
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void emptyRepositoryResultKeepsPageMetadata() {
        AnalyticsSessionUsageQueryService service = new AnalyticsSessionUsageQueryService(
                new FakeSessionUsageRepository(
                        new PageResponse<>(List.of(), 2, 20, 0),
                        new PageResponse<>(List.of(), 2, 20, 0)));

        PageResponse<AnalyticsModels.SessionUsageRow> page = service.sessionMessageUsage(filter(2));

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.total()).isZero();
    }

    @Test
    void summaryReturnsRepositoryPage() {
        AnalyticsModels.SessionUsageSummaryRow summaryRow = new AnalyticsModels.SessionUsageSummaryRow(
                "usr_1", "张三", "AUTH_1", "总行", "研发一部", "平台部", 3, 9, NOW, NOW.plusSeconds(60));
        AnalyticsSessionUsageQueryService service = new AnalyticsSessionUsageQueryService(
                new FakeSessionUsageRepository(
                        new PageResponse<>(List.of(), 1, 20, 0),
                        new PageResponse<>(List.of(summaryRow), 1, 20, 1)));

        PageResponse<AnalyticsModels.SessionUsageSummaryRow> page = service.sessionMessageSummary(filter());

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).sessionCount()).isEqualTo(3);
        assertThat(page.items().get(0).userMessageCount()).isEqualTo(9);
        assertThat(page.items().get(0).unifiedAuthId()).isEqualTo("AUTH_1");
        assertThat(page.total()).isEqualTo(1);
    }

    private static AnalyticsModels.Filter filter() {
        return filter(1);
    }

    private static AnalyticsModels.Filter filter(int page) {
        return new AnalyticsModels.Filter(
                NOW.minusSeconds(86_400),
                NOW,
                AnalyticsModels.Granularity.DAY,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                100,
                page,
                20,
                "active");
    }

    /** 固定返回明细/汇总两页，用于验证查询服务透传。 */
    private record FakeSessionUsageRepository(
            PageResponse<AnalyticsModels.SessionUsageRow> usage,
            PageResponse<AnalyticsModels.SessionUsageSummaryRow> summary)
            implements AnalyticsSessionUsageRepository {

        @Override
        public PageResponse<AnalyticsModels.SessionUsageRow> sessionMessageUsage(AnalyticsModels.Filter filter) {
            return usage;
        }

        @Override
        public PageResponse<AnalyticsModels.SessionUsageSummaryRow> sessionMessageSummary(AnalyticsModels.Filter filter) {
            return summary;
        }
    }
}
