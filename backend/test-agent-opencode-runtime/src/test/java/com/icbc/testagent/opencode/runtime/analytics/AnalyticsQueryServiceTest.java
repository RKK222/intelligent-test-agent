package com.icbc.testagent.opencode.runtime.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.analytics.AnalyticsModels;
import com.icbc.testagent.domain.analytics.AnalyticsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalyticsQueryServiceTest {

    private static final Instant START = Instant.parse("2026-06-28T00:00:00Z");
    private static final Instant END = Instant.parse("2026-06-29T00:00:00Z");

    @Test
    void overviewComputesRatesFromRollupsAndHistogram() {
        AnalyticsQueryService service = new AnalyticsQueryService(new FakeAnalyticsRepository(List.of(row())));
        AnalyticsModels.Filter filter = service.filter(START, END, "day", null, null, null, null, null, null, null, 10, 1, 20, null);

        AnalyticsModels.Overview overview = service.overview(filter);

        assertThat(overview.registeredUsers()).isEqualTo(12);
        assertThat(overview.enabledUsers()).isEqualTo(10);
        assertThat(overview.activeUsers()).isEqualTo(1);
        assertThat(overview.validUsers()).isEqualTo(1);
        assertThat(overview.activeRate()).isEqualTo(0.1d);
        assertThat(overview.successRate()).isEqualTo(0.5d);
        assertThat(overview.failureRate()).isEqualTo(0.5d);
        assertThat(overview.satisfactionRate()).isEqualTo(0.5d);
        assertThat(overview.feedbackCoverageRate()).isEqualTo(1.0d);
        assertThat(overview.diffAcceptanceRate()).isEqualTo(0.5d);
        assertThat(overview.p95DurationMs()).isEqualTo(42_000L);
        assertThat(overview.totalTokens()).isEqualTo(21);
    }

    @Test
    void overviewReturnsNullRatesWhenDenominatorIsEmpty() {
        AnalyticsQueryService service = new AnalyticsQueryService(new FakeAnalyticsRepository(List.of()));
        AnalyticsModels.Filter filter = service.filter(START, END, "day", null, null, null, null, null, null, null, 10, 1, 20, null);

        AnalyticsModels.Overview overview = service.overview(filter);

        assertThat(overview.satisfactionRate()).isNull();
        assertThat(overview.diffAcceptanceRate()).isNull();
        assertThat(overview.tokensPerRun()).isNull();
    }

    @Test
    void filterRejectsOversizedPagesAndHourRanges() {
        AnalyticsQueryService service = new AnalyticsQueryService(new FakeAnalyticsRepository(List.of()));

        assertThatThrownBy(() -> service.filter(START, END.plus(2, ChronoUnit.DAYS), "hour", null, null, null, null, null, null, null, 10, 1, 20, null))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.filter(START, END, "day", null, null, null, null, null, null, null, 101, 1, 20, null))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.filter(START, END, "day", null, null, null, null, null, null, null, 10, 1, 101, null))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void csvExportsDoNotExposeCostFields() {
        AnalyticsQueryService service = new AnalyticsQueryService(new FakeAnalyticsRepository(List.of(row())));
        AnalyticsModels.Filter filter = service.filter(START, END, "day", null, null, null, null, null, null, null, 10, 1, 20, null);

        String csv = service.exportCsv(filter, "overview");

        assertThat(csv.toLowerCase(java.util.Locale.ROOT)).doesNotContain("cost");
        assertThat(csv).doesNotContain("costUsd");
        assertThat(csv).contains("totalTokens");
    }

    private static AnalyticsModels.ActivityRollupRow row() {
        return new AnalyticsModels.ActivityRollupRow(
                null,
                LocalDate.parse("2026-06-28"),
                "usr_analytics12345",
                "alice",
                "总行",
                "研发一部",
                "效能平台",
                "wrk_analytics12345",
                "opencode",
                "gpt-5",
                1,
                1,
                1,
                0,
                1,
                3,
                2,
                2,
                1,
                1,
                0,
                0,
                2,
                1,
                1,
                2,
                1,
                1,
                10,
                8,
                3,
                21,
                60_000,
                2,
                START,
                START.plusSeconds(60));
    }

    private record FakeAnalyticsRepository(List<AnalyticsModels.ActivityRollupRow> rows) implements AnalyticsRepository {
        @Override
        public List<AnalyticsModels.RawActivityRow> loadRawActivityFacts(Instant startInclusive, Instant endExclusive) {
            return List.of();
        }

        @Override
        public List<AnalyticsModels.DurationSample> loadRunDurationSamples(Instant startInclusive, Instant endExclusive) {
            return List.of();
        }

        @Override
        public void deleteHourly(Instant startInclusive, Instant endExclusive) {
        }

        @Override
        public void insertHourly(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt) {
        }

        @Override
        public List<AnalyticsModels.ActivityRollupRow> loadHourly(Instant startInclusive, Instant endExclusive) {
            return rows;
        }

        @Override
        public void deleteDaily(LocalDate startInclusive, LocalDate endInclusive) {
        }

        @Override
        public void insertDaily(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt) {
        }

        @Override
        public void deleteDurationHistogram(Instant startInclusive, Instant endExclusive) {
        }

        @Override
        public void insertDurationHistogram(List<AnalyticsModels.DurationHistogramRow> histogramRows, Instant updatedAt) {
        }

        @Override
        public Optional<AnalyticsModels.Freshness> freshness(String jobName, Instant staleThreshold) {
            return Optional.of(new AnalyticsModels.Freshness(END, AnalyticsModels.FreshnessStatus.FRESH, null));
        }

        @Override
        public void updateWatermark(
                String jobName,
                Instant watermarkAt,
                AnalyticsModels.FreshnessStatus status,
                String message,
                String traceId,
                Instant updatedAt) {
        }

        @Override
        public boolean tryAcquireLock(String lockName, String ownerId, Instant lockedUntil, Instant now) {
            return true;
        }

        @Override
        public void releaseLock(String lockName, String ownerId) {
        }

        @Override
        public long countRegisteredUsers(AnalyticsModels.Filter filter) {
            return 12;
        }

        @Override
        public long countEnabledUsers(AnalyticsModels.Filter filter) {
            return 10;
        }

        @Override
        public List<AnalyticsModels.ActivityRollupRow> queryRollups(AnalyticsModels.Filter filter) {
            return rows;
        }

        @Override
        public long approximateP95DurationMs(AnalyticsModels.Filter filter) {
            return rows.isEmpty() ? 0 : 42_000;
        }

        @Override
        public PageResponse<AnalyticsModels.FeedbackDetail> feedbackDetails(AnalyticsModels.Filter filter) {
            return new PageResponse<>(List.of(), filter.page(), filter.pageSize(), 0);
        }

        @Override
        public Map<String, Long> negativeReasonCounts(AnalyticsModels.Filter filter) {
            return Map.of("WRONG_ANSWER", 1L);
        }

        @Override
        public PageResponse<AnalyticsModels.ExceptionDetail> exceptionDetails(AnalyticsModels.Filter filter) {
            return new PageResponse<>(List.of(), filter.page(), filter.pageSize(), 0);
        }

        @Override
        public List<AnalyticsModels.OrganizationUsageRow> organizationRows(AnalyticsModels.Filter filter, String dimension) {
            return List.of();
        }
    }
}
