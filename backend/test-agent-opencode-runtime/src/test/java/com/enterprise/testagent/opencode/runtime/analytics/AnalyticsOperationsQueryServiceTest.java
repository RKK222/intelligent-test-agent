package com.enterprise.testagent.opencode.runtime.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.Test;

class AnalyticsOperationsQueryServiceTest {

    private static final Instant START = Instant.parse("2026-06-27T16:00:00Z");
    private static final Instant END = Instant.parse("2026-06-30T16:00:00Z");

    @Test
    void funnelRequiresMessagesOnTwoShanghaiDatesAndAtLeastFiveMessagesForDeepUsers() {
        AnalyticsQueryService service = new AnalyticsQueryService(new FakeAnalyticsRepository(List.of(
                row("usr_deep", LocalDate.parse("2026-06-28"), 3, 10, 5, 0, 0),
                row("usr_deep", LocalDate.parse("2026-06-29"), 2, 0, 0, 0, 0),
                row("usr_one_day", LocalDate.parse("2026-06-28"), 8, 0, 0, 0, 0),
                row("usr_login", LocalDate.parse("2026-06-28"), 0, 0, 0, 0, 1))));

        AnalyticsModels.Funnel funnel = service.funnel(filter(service));

        assertThat(funnel.totalUsers()).isEqualTo(4);
        assertThat(funnel.activeUsers()).isEqualTo(2);
        assertThat(funnel.deepUsers()).isEqualTo(1);
        assertThat(funnel.activeRate()).isEqualTo(0.5d);
        assertThat(funnel.deepRate()).isEqualTo(0.5d);
        assertThat(funnel.activeDefinition()).contains("至少发送 1 条用户消息");
        assertThat(funnel.deepDefinition()).contains("至少 2 个自然日").contains("至少 5 条用户消息");
    }

    @Test
    void hourlyHeatmapUsesShanghaiDatesAndFillsEveryDateHourCell() {
        AnalyticsQueryService service = new AnalyticsQueryService(new FakeAnalyticsRepository(List.of(
                hourlyRow("usr_a", Instant.parse("2026-06-27T16:00:00Z"), 2, 7, 1, 0),
                hourlyRow("usr_b", Instant.parse("2026-06-28T15:00:00Z"), 1, 3, 2, 0))));

        AnalyticsModels.HourlyHeatmap heatmap = service.hourlyHeatmap(
                filter(service), AnalyticsModels.HeatmapMetric.USER_MESSAGES);

        assertThat(heatmap.dates()).containsExactly(
                LocalDate.parse("2026-06-28"),
                LocalDate.parse("2026-06-29"),
                LocalDate.parse("2026-06-30"));
        assertThat(heatmap.points()).hasSize(72);
        assertThat(heatmap.points())
                .filteredOn(point -> point.date().equals(LocalDate.parse("2026-06-28")) && point.hourOfDay() == 0)
                .singleElement()
                .extracting(AnalyticsModels.HourlyHeatmapPoint::value)
                .isEqualTo(2L);
        assertThat(heatmap.points())
                .filteredOn(point -> point.date().equals(LocalDate.parse("2026-06-29")) && point.hourOfDay() == 0)
                .singleElement()
                .extracting(AnalyticsModels.HourlyHeatmapPoint::value)
                .isEqualTo(0L);
    }

    @Test
    void tokenOperationsUseOnlyTokenActivePersonDaysAsDailyAverageDenominator() {
        AnalyticsQueryService service = new AnalyticsQueryService(new FakeAnalyticsRepository(List.of(
                row("usr_a", LocalDate.parse("2026-06-28"), 1, 70, 30, 10, 5),
                row("usr_a", LocalDate.parse("2026-06-29"), 1, 30, 10, 0, 0),
                row("usr_b", LocalDate.parse("2026-06-28"), 1, 0, 0, 0, 20),
                row("usr_c", LocalDate.parse("2026-06-28"), 1, 0, 0, 0, 0))));

        AnalyticsModels.TokenOperations operations = service.tokenOperations(filter(service));

        assertThat(operations.totalTokens()).isEqualTo(175);
        assertThat(operations.primaryTokens()).isEqualTo(140);
        assertThat(operations.cacheReadTokens()).isEqualTo(10);
        assertThat(operations.cacheWriteTokens()).isEqualTo(25);
        assertThat(operations.tokenUsers()).isEqualTo(2);
        assertThat(operations.tokenActivePersonDays()).isEqualTo(3);
        assertThat(operations.dailyTokensPerUser()).isEqualTo(175.0d / 3.0d);
        assertThat(operations.repeatTokenUsers()).isEqualTo(1);
        assertThat(operations.repeatTokenUserRate()).isEqualTo(0.5d);
    }

    @Test
    void capabilityUsageCountsStartedFailuresAndUsesActiveUsersAsDenominator() {
        FakeAnalyticsRepository repository = new FakeAnalyticsRepository(List.of(
                row("usr_a", LocalDate.parse("2026-06-28"), 1, 0, 0, 0, 0),
                row("usr_b", LocalDate.parse("2026-06-28"), 1, 0, 0, 0, 0),
                row("usr_c", LocalDate.parse("2026-06-28"), 1, 0, 0, 0, 0)),
                List.of(
                        new AnalyticsModels.CapabilityUsageRow("TOOL", "bash", 4, 2, 2, 1, 0, 1),
                        new AnalyticsModels.CapabilityUsageRow("SKILL", "review", 1, 1, 0, 1, 0, 0)));
        AnalyticsQueryService service = new AnalyticsQueryService(repository);

        AnalyticsModels.Capabilities capabilities = service.capabilities(filter(service));

        assertThat(capabilities.activeUsers()).isEqualTo(3);
        assertThat(capabilities.rows()).first().satisfies(row -> {
            assertThat(row.name()).isEqualTo("bash");
            assertThat(row.invocationCount()).isEqualTo(4);
            assertThat(row.usageRate()).isEqualTo(2.0d / 3.0d);
            assertThat(row.failedCount()).isEqualTo(1);
            assertThat(row.cancelledCount()).isZero();
        });
    }

    @Test
    void simplifiedFilterRejectsLegacyDimensionsAndHeatmapRangesOverNinetyDays() {
        AnalyticsQueryService service = new AnalyticsQueryService(new FakeAnalyticsRepository(List.of()));

        assertThatThrownBy(() -> service.filter(
                START, END, null, null, null, null, null, "opencode", null, null, 10, 1, 20, null))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        AnalyticsModels.Filter longRange = service.filter(
                START, START.plusSeconds(91L * 86_400L), null, null, null, null, null, null, null, null,
                10, 1, 20, null);
        assertThatThrownBy(() -> service.hourlyHeatmap(longRange, AnalyticsModels.HeatmapMetric.PRIMARY_TOKENS))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private static AnalyticsModels.Filter filter(AnalyticsQueryService service) {
        return service.filter(START, END, null, null, null, null, "alice", null, null, null, 10, 1, 20, null);
    }

    private static AnalyticsModels.ActivityRollupRow row(
            String userId,
            LocalDate date,
            long userMessages,
            long inputTokens,
            long outputTokens,
            long cacheReadTokens,
            long cacheWriteTokens) {
        return new AnalyticsModels.ActivityRollupRow(
                null, date, userId, userId, "总行", "研发一部", "效能平台", "__none__", "__none__", "__none__",
                userMessages == 0 ? 1 : 0, 0, 0, 0, 0, userMessages, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                inputTokens, outputTokens, 0, cacheReadTokens, cacheWriteTokens,
                inputTokens + outputTokens, 0, 0,
                date.atStartOfDay(AnalyticsQueryService.ANALYTICS_ZONE).toInstant(),
                date.atStartOfDay(AnalyticsQueryService.ANALYTICS_ZONE).toInstant());
    }

    private static AnalyticsModels.ActivityRollupRow hourlyRow(
            String userId,
            Instant bucket,
            long userMessages,
            long inputTokens,
            long outputTokens,
            long cacheReadTokens) {
        return new AnalyticsModels.ActivityRollupRow(
                bucket, null, userId, userId, "总行", "研发一部", "效能平台", "__none__", "__none__", "__none__",
                0, 0, 0, 0, 0, userMessages, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                inputTokens, outputTokens, 0, cacheReadTokens, 0,
                inputTokens + outputTokens, 0, 0, bucket, bucket);
    }

    private record FakeAnalyticsRepository(
            List<AnalyticsModels.ActivityRollupRow> rows,
            List<AnalyticsModels.CapabilityUsageRow> capabilityRows) implements AnalyticsRepository {

        private FakeAnalyticsRepository(List<AnalyticsModels.ActivityRollupRow> rows) {
            this(rows, List.of());
        }

        @Override public List<AnalyticsModels.RawActivityRow> loadRawActivityFacts(Instant start, Instant end) { return List.of(); }
        @Override public List<AnalyticsModels.DurationSample> loadRunDurationSamples(Instant start, Instant end) { return List.of(); }
        @Override public void deleteHourly(Instant start, Instant end) { }
        @Override public void insertHourly(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt) { }
        @Override public List<AnalyticsModels.ActivityRollupRow> loadHourly(Instant start, Instant end) { return rows; }
        @Override public void deleteDaily(LocalDate start, LocalDate end) { }
        @Override public void insertDaily(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt) { }
        @Override public void deleteDurationHistogram(Instant start, Instant end) { }
        @Override public void insertDurationHistogram(List<AnalyticsModels.DurationHistogramRow> rows, Instant updatedAt) { }
        @Override public Optional<AnalyticsModels.Freshness> freshness(String job, Instant threshold) {
            return Optional.of(new AnalyticsModels.Freshness(END, AnalyticsModels.FreshnessStatus.FRESH, null));
        }
        @Override public void updateWatermark(String job, Instant watermark, AnalyticsModels.FreshnessStatus status,
                                              String message, String traceId, Instant updatedAt) { }
        @Override public boolean tryAcquireLock(String name, String owner, Instant until, Instant now) { return true; }
        @Override public void releaseLock(String name, String owner) { }
        @Override public long countRegisteredUsers(AnalyticsModels.Filter filter) { return 4; }
        @Override public long countEnabledUsers(AnalyticsModels.Filter filter) { return 4; }
        @Override public List<AnalyticsModels.ActivityRollupRow> queryRollups(AnalyticsModels.Filter filter) { return rows; }
        @Override public long approximateP95DurationMs(AnalyticsModels.Filter filter) { return 0; }
        @Override public PageResponse<AnalyticsModels.FeedbackDetail> feedbackDetails(AnalyticsModels.Filter filter) {
            return new PageResponse<>(List.of(), filter.page(), filter.pageSize(), 0);
        }
        @Override public Map<String, Long> negativeReasonCounts(AnalyticsModels.Filter filter) { return Map.of(); }
        @Override public PageResponse<AnalyticsModels.ExceptionDetail> exceptionDetails(AnalyticsModels.Filter filter) {
            return new PageResponse<>(List.of(), filter.page(), filter.pageSize(), 0);
        }
        @Override public List<AnalyticsModels.OrganizationUsageRow> organizationRows(AnalyticsModels.Filter filter, String dimension) {
            return List.of();
        }
        @Override public List<AnalyticsModels.CapabilityUsageRow> capabilityUsage(AnalyticsModels.Filter filter) {
            return capabilityRows;
        }
    }
}
