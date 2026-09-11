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
        AnalyticsSessionUsageRepository repository = filter -> new PageResponse<>(
                List.of(row), filter.page(), filter.pageSize(), 1);
        AnalyticsSessionUsageQueryService service = new AnalyticsSessionUsageQueryService(repository);

        PageResponse<AnalyticsModels.SessionUsageRow> page = service.sessionMessageUsage(filter());

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).userMessageCount()).isEqualTo(3);
        assertThat(page.items().get(0).sessionTitle()).isEqualTo("会话标题");
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void emptyRepositoryResultKeepsPageMetadata() {
        AnalyticsSessionUsageQueryService service =
                new AnalyticsSessionUsageQueryService(filter -> new PageResponse<>(List.of(), 2, 20, 0));

        PageResponse<AnalyticsModels.SessionUsageRow> page = service.sessionMessageUsage(filter(2));

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.total()).isZero();
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
}
