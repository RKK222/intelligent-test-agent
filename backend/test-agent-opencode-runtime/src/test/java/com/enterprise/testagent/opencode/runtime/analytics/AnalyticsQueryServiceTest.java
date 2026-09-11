package com.enterprise.testagent.opencode.runtime.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackRating;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackReasonCode;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.analytics.AnalyticsRepository;
import com.enterprise.testagent.domain.analytics.AnalyticsSessionUsageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
    void defaultsToThirtyDaysAndFillsMissingTrendBuckets() {
        AnalyticsQueryService service = new AnalyticsQueryService(new FakeAnalyticsRepository(List.of(row())));

        AnalyticsModels.Filter defaultFilter = service.filter(null, END, "day", null, null, null, null, null, null, null, 10, 1, 20, null);
        AnalyticsModels.Filter threeDayFilter = service.filter(
                START,
                START.plus(3, ChronoUnit.DAYS),
                "day",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                10,
                1,
                20,
                null);

        assertThat(defaultFilter.startTime()).isEqualTo(END.minus(30, ChronoUnit.DAYS));
        assertThat(service.timeseries(threeDayFilter))
                .extracting(AnalyticsModels.TimeSeriesPoint::bucketStart, AnalyticsModels.TimeSeriesPoint::runCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Instant.parse("2026-06-27T16:00:00Z"), 2L),
                        org.assertj.core.groups.Tuple.tuple(Instant.parse("2026-06-28T16:00:00Z"), 0L),
                        org.assertj.core.groups.Tuple.tuple(Instant.parse("2026-06-29T16:00:00Z"), 0L),
                        org.assertj.core.groups.Tuple.tuple(Instant.parse("2026-06-30T16:00:00Z"), 0L));
    }

    @Test
    void peaksAlwaysReturnsSevenByTwentyFourHeatmap() {
        AnalyticsQueryService service = new AnalyticsQueryService(new FakeAnalyticsRepository(List.of(row())));
        AnalyticsModels.Filter filter = service.filter(START, END, "day", null, null, null, null, null, null, null, 10, 1, 20, null);

        AnalyticsModels.Peaks peaks = service.peaks(filter);

        assertThat(peaks.heatmap()).hasSize(168);
        assertThat(peaks.heatmap().getFirst()).isEqualTo(new AnalyticsModels.HeatmapPoint(1, 0, 0, 0, 0));
        assertThat(peaks.heatmap())
                .filteredOn(point -> point.dayOfWeek() == 7 && point.hourOfDay() == 0)
                .containsExactly(new AnalyticsModels.HeatmapPoint(7, 0, 1, 2, 3));
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

    /**
     * 用同一份固定数据覆盖改造前运营页的全部查询和导出，摘要可直接在历史基线与当前代码间比较。
     * 新增的插件覆盖水位、取消次数属于向后兼容字段，不参与旧字段摘要。
     */
    @Test
    void originalOperationsMetricsMatchTheFixedDatasetBaseline() throws Exception {
        AnalyticsQueryService service = new AnalyticsQueryService(new FakeAnalyticsRepository(List.of(row())));
        AnalyticsModels.Filter filter = service.filter(
                START, END, "day", null, null, null, null, null, null, null, 10, 1, 20, null);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("filterOptions", service.filterOptions(filter));
        snapshot.put("funnel", service.funnel(filter));
        for (AnalyticsModels.HeatmapMetric metric : AnalyticsModels.HeatmapMetric.values()) {
            snapshot.put("hourlyHeatmap." + metric, service.hourlyHeatmap(filter, metric));
        }
        snapshot.put("tokenOperations", service.tokenOperations(filter));
        snapshot.put("capabilities", service.capabilities(filter));
        snapshot.put("overview", service.overview(filter));
        snapshot.put("timeseries", service.timeseries(filter));
        snapshot.put("peaks", service.peaks(filter));
        snapshot.put("users", service.users(filter));
        snapshot.put("organizations.organization", service.organizations(filter, "organization"));
        snapshot.put("organizations.rdDepartment", service.organizations(filter, "rdDepartment"));
        snapshot.put("organizations.department", service.organizations(filter, "department"));
        snapshot.put("satisfaction", service.satisfaction(filter));
        snapshot.put("feedbackDetails", service.feedbackDetails(filter));
        snapshot.put("exceptionDetails", service.exceptionDetails(filter));
        for (String type : List.of(
                "overview", "timeseries", "users", "organizations", "feedback", "exceptions", "funnel",
                "token-operations", "capabilities")) {
            String csv = service.exportCsv(filter, type);
            snapshot.put("csv." + type, "capabilities".equals(type) ? legacyCapabilitiesCsv(csv) : csv);
        }

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JsonNode normalized = mapper.valueToTree(snapshot);
        removePluginAdditiveFields(normalized);
        String canonical = mapper.writer()
                .with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(normalized);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));

        assertThat(digest).isEqualTo("ff77d42df433913e460f2bc281084b50530cfb1dc8bc8f18e2a2e3482b06e012");
    }

    /**
     * 「会话消息」Sheet 的数据来自业务库独立查询服务，必须翻页取满全部行，
     * 不能只导出首页 20 条，也不能因为单页上限 200 就丢掉后续页。
     */
    @Test
    void exportAllIncludesSessionUsageSheetAcrossAllPages() throws Exception {
        List<AnalyticsModels.SessionUsageRow> sessionRows = java.util.stream.IntStream.range(0, 250)
                .mapToObj(index -> new AnalyticsModels.SessionUsageRow(
                        "usr_" + index, "用户" + index, "ses_" + index, "会话" + index, index + 1L,
                        START.plusSeconds(index), START.plusSeconds(index + 1)))
                .toList();
        AnalyticsQueryService service = new AnalyticsQueryService(
                new FakeAnalyticsRepository(List.of(row())),
                new AnalyticsSessionUsageQueryService(new FakeSessionUsageRepository(sessionRows)));
        AnalyticsModels.Filter filter =
                service.filter(START, END, "day", null, null, null, null, null, null, null, 10, 1, 20, null);

        byte[] xlsx = service.exportAllXlsx(filter);

        try (org.apache.poi.ss.usermodel.Workbook workbook =
                new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheet("会话消息");
            assertThat(sheet).isNotNull();
            // 表头 1 行 + 250 条数据，跨两页取满
            assertThat(sheet.getLastRowNum()).isEqualTo(250);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("用户名");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("最后发送时间");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("会话0");
            assertThat(sheet.getRow(250).getCell(2).getNumericCellValue()).isEqualTo(250d);
            assertThat(sheet.getRow(250).getCell(3).getStringCellValue()).isEqualTo("2026-06-28 08:04:09");
        }
    }

    private static void removePluginAdditiveFields(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove(List.of(
                    "cancelledCount", "source", "coverageStartAt", "completeThrough", "rolloutCompleteness"));
            object.elements().forEachRemaining(AnalyticsQueryServiceTest::removePluginAdditiveFields);
            return;
        }
        if (node.isArray()) {
            node.elements().forEachRemaining(AnalyticsQueryServiceTest::removePluginAdditiveFields);
        }
    }

    private static String legacyCapabilitiesCsv(String csv) {
        return csv.lines().map(line -> {
            String[] values = line.split(",", -1);
            if (values.length != 9) {
                return line;
            }
            return String.join(",", values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[8]);
        }).collect(java.util.stream.Collectors.joining("\n", "", "\n"));
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
                0,
                0,
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
            return new PageResponse<>(List.of(new AnalyticsModels.FeedbackDetail(
                    "fb_analytics12345", "usr_analytics12345", "alice", "总行", "研发一部", "效能平台",
                    "ses_analytics12345", "run_analytics12345", "msg_analytics12345",
                    AiMessageFeedbackRating.NEGATIVE, AiMessageFeedbackReasonCode.WRONG_ANSWER, "固定反馈",
                    START.plusSeconds(30), START.plusSeconds(31))), filter.page(), filter.pageSize(), 1);
        }

        @Override
        public Map<String, Long> negativeReasonCounts(AnalyticsModels.Filter filter) {
            return Map.of("WRONG_ANSWER", 1L);
        }

        @Override
        public PageResponse<AnalyticsModels.ExceptionDetail> exceptionDetails(AnalyticsModels.Filter filter) {
            return new PageResponse<>(List.of(new AnalyticsModels.ExceptionDetail(
                    "run_analytics12345", "usr_analytics12345", "alice", "总行", "研发一部", "效能平台",
                    "wrk_analytics12345", "opencode", "gpt-5", "FAILED", START, START.plusSeconds(60))),
                    filter.page(), filter.pageSize(), 1);
        }

        @Override
        public List<AnalyticsModels.OrganizationUsageRow> organizationRows(AnalyticsModels.Filter filter, String dimension) {
            return List.of(new AnalyticsModels.OrganizationUsageRow(
                    dimension, "固定组织", 12, 10, 1, 1, 1, 0.1d, 1.0d, 2, 1, 1, 0,
                    1, 1, 1, 1, 21, 0.5d, 0.5d, 0.5d));
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<AnalyticsModels.CapabilityUsageRow> capabilityUsage(AnalyticsModels.Filter filter) {
            try {
                var constructor = AnalyticsModels.CapabilityUsageRow.class.getDeclaredConstructors()[0];
                Object[] values = constructor.getParameterCount() == 8
                        ? new Object[] {"SKILL", "test-design", 1L, 1L, 1L, 0L, 0L, 0L}
                        : new Object[] {"SKILL", "test-design", 1L, 1L, 1L, 0L, 0L};
                return List.of((AnalyticsModels.CapabilityUsageRow) constructor.newInstance(values));
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("无法创建跨版本能力固定数据", exception);
            }
        }

        @Override
        public List<AnalyticsModels.FilterOption> organizations() {
            return List.of(new AnalyticsModels.FilterOption("总行", "总行"));
        }

        @Override
        public List<AnalyticsModels.FilterOption> rdDepartments(String organization) {
            return List.of(new AnalyticsModels.FilterOption("研发一部", "研发一部"));
        }

        @Override
        public List<AnalyticsModels.FilterOption> departments(String organization, String rdDepartment) {
            return List.of(new AnalyticsModels.FilterOption("效能平台", "效能平台"));
        }
    }

    /** 按页切片返回业务库会话消息统计，用于验证导出翻页取满。 */
    private record FakeSessionUsageRepository(List<AnalyticsModels.SessionUsageRow> rows)
            implements AnalyticsSessionUsageRepository {

        @Override
        public PageResponse<AnalyticsModels.SessionUsageRow> sessionMessageUsage(AnalyticsModels.Filter filter) {
            int size = filter.pageSize();
            int from = Math.min((filter.page() - 1) * size, rows.size());
            int to = Math.min(from + size, rows.size());
            return new PageResponse<>(rows.subList(from, to), filter.page(), size, rows.size());
        }

        @Override
        public PageResponse<AnalyticsModels.SessionUsageSummaryRow> sessionMessageSummary(AnalyticsModels.Filter filter) {
            return new PageResponse<>(List.of(), filter.page(), filter.pageSize(), 0);
        }
    }
}
