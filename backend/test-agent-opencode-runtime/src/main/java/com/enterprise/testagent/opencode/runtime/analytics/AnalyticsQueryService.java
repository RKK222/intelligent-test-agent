package com.enterprise.testagent.opencode.runtime.analytics;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.analytics.AnalyticsRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 运营分析查询服务。所有聚合接口只读 rollup 表，避免请求时扫描原始大表。
 */
@Service
public class AnalyticsQueryService {

    private static final int MAX_TOP_N = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_POINTS = 500;

    private final AnalyticsRepository repository;

    public AnalyticsQueryService(AnalyticsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 规范化并校验查询过滤器，保护 rollup 查询点数和明细页大小。
     */
    public AnalyticsModels.Filter filter(
            Instant startTime,
            Instant endTime,
            String granularityValue,
            String organization,
            String rdDepartment,
            String department,
            String userId,
            String agentId,
            String model,
            String workspaceId,
            Integer topN,
            Integer page,
            Integer pageSize,
            String sort) {
        Instant end = endTime == null ? Instant.now() : endTime;
        Instant start = startTime == null ? end.minus(7, ChronoUnit.DAYS) : startTime;
        if (!start.isBefore(end)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "startTime 必须早于 endTime");
        }
        long days = Duration.between(start, end).toDays();
        if (days > 366) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "查询时间范围不能超过 366 天");
        }
        AnalyticsModels.Granularity granularity = granularity(granularityValue, start, end);
        validateGranularity(start, end, granularity);
        int resolvedTopN = topN == null ? 10 : topN;
        if (resolvedTopN < 1 || resolvedTopN > MAX_TOP_N) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "topN 必须在 1 到 100 之间");
        }
        int resolvedPageSize = pageSize == null ? 20 : pageSize;
        if (resolvedPageSize < 1 || resolvedPageSize > MAX_PAGE_SIZE) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "pageSize 必须在 1 到 100 之间");
        }
        int resolvedPage = page == null || page < 1 ? 1 : page;
        return new AnalyticsModels.Filter(
                start,
                end,
                granularity,
                blankToNull(organization),
                blankToNull(rdDepartment),
                blankToNull(department),
                blankToNull(userId),
                blankToNull(agentId),
                blankToNull(model),
                blankToNull(workspaceId),
                resolvedTopN,
                resolvedPage,
                resolvedPageSize,
                blankToNull(sort));
    }

    public AnalyticsModels.Overview overview(AnalyticsModels.Filter filter) {
        List<AnalyticsModels.ActivityRollupRow> rows = repository.queryRollups(filter);
        Totals totals = totals(rows);
        long registeredUsers = repository.countRegisteredUsers(filter);
        long enabledUsers = repository.countEnabledUsers(filter);
        Long averageDuration = totals.durationRunCount == 0 ? null : totals.durationTotalMs / totals.durationRunCount;
        long p95 = repository.approximateP95DurationMs(filter);
        return new AnalyticsModels.Overview(
                registeredUsers,
                enabledUsers,
                totals.loginUsers.size(),
                totals.activeUsers.size(),
                totals.validUsers.size(),
                totals.deepUsers(),
                ratio(totals.activeUsers.size(), enabledUsers),
                ratio(totals.activeUsers.size(), totals.loginUsers.size()),
                ratio(totals.validUsers.size(), totals.activeUsers.size()),
                ratio(totals.deepUsers(), totals.validUsers.size()),
                totals.sessionCount,
                totals.activeSessionCount,
                totals.emptySessionCount,
                totals.continuousSessionCount,
                totals.userMessageCount,
                totals.assistantMessageCount,
                totals.runCount,
                ratio(totals.runCount, totals.activeUsers.size()),
                ratio(totals.userMessageCount, totals.activeUsers.size()),
                ratio(totals.userMessageCount, totals.activeSessionCount),
                ratio(totals.continuousSessionCount, totals.activeSessionCount),
                totals.validInteractionCount,
                totals.sustainedUsers(),
                totals.succeededRuns,
                totals.failedRuns,
                totals.cancelledRuns,
                totals.activeTerminations,
                ratio(totals.succeededRuns, totals.runCount),
                ratio(totals.failedRuns, totals.runCount),
                ratio(totals.cancelledRuns, totals.runCount),
                averageDuration,
                p95 == 0 ? null : p95,
                totals.positiveFeedback,
                totals.negativeFeedback,
                ratio(totals.positiveFeedback, totals.positiveFeedback + totals.negativeFeedback),
                ratio(totals.positiveFeedback + totals.negativeFeedback, totals.assistantMessageCount),
                totals.diffProposed,
                totals.diffAccepted,
                totals.diffRejected,
                ratio(totals.diffAccepted, totals.diffProposed),
                ratio(totals.diffRejected, totals.diffProposed),
                totals.tokensInput,
                totals.tokensOutput,
                totals.tokensReasoning,
                totals.tokensTotal(),
                ratio(totals.tokensTotal(), totals.activeUsers.size()),
                ratio(totals.tokensTotal(), totals.runCount),
                freshness());
    }

    public List<AnalyticsModels.TimeSeriesPoint> timeseries(AnalyticsModels.Filter filter) {
        Map<Instant, BucketTotals> grouped = new TreeMap<>();
        for (AnalyticsModels.ActivityRollupRow row : repository.queryRollups(filter)) {
            Instant bucket = bucket(row, filter.granularity());
            grouped.computeIfAbsent(bucket, ignored -> new BucketTotals()).add(row);
        }
        return grouped.entrySet().stream()
                .map(entry -> entry.getValue().toPoint(entry.getKey()))
                .toList();
    }

    public AnalyticsModels.Peaks peaks(AnalyticsModels.Filter filter) {
        AnalyticsModels.Filter hourly = new AnalyticsModels.Filter(
                filter.startTime(),
                filter.endTime(),
                AnalyticsModels.Granularity.HOUR,
                filter.organization(),
                filter.rdDepartment(),
                filter.department(),
                filter.userId(),
                filter.agentId(),
                filter.model(),
                filter.workspaceId(),
                Math.min(filter.topN(), 5),
                filter.page(),
                filter.pageSize(),
                filter.sort());
        Map<Instant, BucketTotals> grouped = new TreeMap<>();
        for (AnalyticsModels.ActivityRollupRow row : repository.queryRollups(hourly)) {
            grouped.computeIfAbsent(row.bucketStart(), ignored -> new BucketTotals()).add(row);
        }
        List<AnalyticsModels.PeakPoint> peakPeriods = grouped.entrySet().stream()
                .map(entry -> entry.getValue().toPeak(entry.getKey()))
                .sorted(Comparator.comparingLong(AnalyticsModels.PeakPoint::activeUsers).reversed()
                        .thenComparing(Comparator.comparingLong(AnalyticsModels.PeakPoint::runCount).reversed()))
                .limit(hourly.topN())
                .toList();
        Map<String, HeatmapTotals> heatmap = new LinkedHashMap<>();
        grouped.forEach((bucket, totals) -> {
            int dayOfWeek = bucket.atZone(ZoneOffset.UTC).getDayOfWeek().getValue();
            int hour = bucket.atZone(ZoneOffset.UTC).getHour();
            heatmap.computeIfAbsent(dayOfWeek + ":" + hour, ignored -> new HeatmapTotals(dayOfWeek, hour))
                    .add(totals);
        });
        return new AnalyticsModels.Peaks(
                peakPeriods,
                heatmap.values().stream().map(HeatmapTotals::toPoint).toList(),
                freshness());
    }

    public PageResponse<AnalyticsModels.UserUsageRow> users(AnalyticsModels.Filter filter) {
        Map<String, UserTotals> grouped = new LinkedHashMap<>();
        for (AnalyticsModels.ActivityRollupRow row : repository.queryRollups(filter)) {
            grouped.computeIfAbsent(row.userId(), ignored -> new UserTotals(row)).add(row);
        }
        List<UserTotals> sorted = grouped.values().stream()
                .sorted(userComparator(filter.sort()))
                .toList();
        int from = Math.min((filter.page() - 1) * filter.pageSize(), sorted.size());
        int to = Math.min(from + filter.pageSize(), sorted.size());
        List<AnalyticsModels.UserUsageRow> items = sorted.subList(from, to).stream()
                .map(UserTotals::toRow)
                .toList();
        return new PageResponse<>(items, filter.page(), filter.pageSize(), sorted.size());
    }

    public List<AnalyticsModels.OrganizationUsageRow> organizations(AnalyticsModels.Filter filter, String dimension) {
        String resolved = Set.of("organization", "rdDepartment", "department").contains(dimension)
                ? dimension
                : "organization";
        return repository.organizationRows(filter, resolved).stream()
                .sorted(orgComparator(filter.sort()))
                .limit(filter.topN())
                .toList();
    }

    public AnalyticsModels.Satisfaction satisfaction(AnalyticsModels.Filter filter) {
        Totals totals = totals(repository.queryRollups(filter));
        return new AnalyticsModels.Satisfaction(
                totals.positiveFeedback,
                totals.negativeFeedback,
                ratio(totals.positiveFeedback, totals.positiveFeedback + totals.negativeFeedback),
                ratio(totals.positiveFeedback + totals.negativeFeedback, totals.assistantMessageCount),
                repository.negativeReasonCounts(filter),
                repository.feedbackDetails(filter),
                freshness());
    }

    public PageResponse<AnalyticsModels.FeedbackDetail> feedbackDetails(AnalyticsModels.Filter filter) {
        return repository.feedbackDetails(filter);
    }

    public PageResponse<AnalyticsModels.ExceptionDetail> exceptionDetails(AnalyticsModels.Filter filter) {
        return repository.exceptionDetails(filter);
    }

    public String exportCsv(AnalyticsModels.Filter filter, String type) {
        String resolvedType = blankToNull(type) == null ? "overview" : type;
        return switch (resolvedType) {
            case "timeseries" -> csvTimeseries(filter);
            case "users" -> csvUsers(filter);
            case "organizations" -> csvOrganizations(filter);
            case "feedback" -> csvFeedback(filter);
            case "exceptions" -> csvExceptions(filter);
            default -> csvOverview(filter);
        };
    }

    private String csvOverview(AnalyticsModels.Filter filter) {
        AnalyticsModels.Overview overview = overview(filter);
        StringBuilder builder = new StringBuilder("metric,value\n");
        append(builder, "registeredUsers", overview.registeredUsers());
        append(builder, "enabledUsers", overview.enabledUsers());
        append(builder, "loginUsers", overview.loginUsers());
        append(builder, "activeUsers", overview.activeUsers());
        append(builder, "validUsers", overview.validUsers());
        append(builder, "deepUsers", overview.deepUsers());
        append(builder, "sessionCount", overview.sessionCount());
        append(builder, "activeSessionCount", overview.activeSessionCount());
        append(builder, "userMessageCount", overview.userMessageCount());
        append(builder, "runCount", overview.runCount());
        append(builder, "satisfactionRate", overview.satisfactionRate());
        append(builder, "diffAcceptanceRate", overview.diffAcceptanceRate());
        append(builder, "totalTokens", overview.totalTokens());
        return builder.toString();
    }

    private String csvTimeseries(AnalyticsModels.Filter filter) {
        StringBuilder builder = new StringBuilder("bucketStart,activeUsers,userMessageCount,runCount,satisfactionRate,diffAcceptanceRate,cancellationRate,totalTokens\n");
        for (AnalyticsModels.TimeSeriesPoint point : timeseries(filter)) {
            row(builder, point.bucketStart(), point.activeUsers(), point.userMessageCount(), point.runCount(),
                    point.satisfactionRate(), point.diffAcceptanceRate(), point.cancellationRate(), point.totalTokens());
        }
        return builder.toString();
    }

    private String csvUsers(AnalyticsModels.Filter filter) {
        StringBuilder builder = new StringBuilder("userId,username,organization,rdDepartment,department,loginCount,activeSessionCount,userMessageCount,runCount,successRate,satisfactionRate,diffAcceptanceRate,totalTokens,lastActivityAt\n");
        for (AnalyticsModels.UserUsageRow row : users(filter).items()) {
            row(builder, row.userId(), row.username(), row.organization(), row.rdDepartment(), row.department(),
                    row.loginCount(), row.activeSessionCount(), row.userMessageCount(), row.runCount(), row.successRate(),
                    row.satisfactionRate(), row.diffAcceptanceRate(), row.totalTokens(), row.lastActivityAt());
        }
        return builder.toString();
    }

    private String csvOrganizations(AnalyticsModels.Filter filter) {
        StringBuilder builder = new StringBuilder("dimension,name,loginUsers,activeUsers,deepUsers,runCount,successRate,satisfactionRate,diffAcceptanceRate,totalTokens\n");
        for (AnalyticsModels.OrganizationUsageRow row : organizations(filter, "organization")) {
            row(builder, row.dimension(), row.name(), row.loginUsers(), row.activeUsers(), row.deepUsers(), row.runCount(),
                    row.successRate(), row.satisfactionRate(), row.diffAcceptanceRate(), row.totalTokens());
        }
        return builder.toString();
    }

    private String csvFeedback(AnalyticsModels.Filter filter) {
        StringBuilder builder = new StringBuilder("createdAt,userId,username,organization,rdDepartment,department,sessionId,runId,messageId,rating,reasonCode,comment\n");
        for (AnalyticsModels.FeedbackDetail row : repository.feedbackDetails(filter).items()) {
            row(builder, row.createdAt(), row.userId(), row.username(), row.organization(), row.rdDepartment(), row.department(),
                    row.sessionId(), row.runId(), row.messageId(), row.rating(), row.reasonCode(), row.comment());
        }
        return builder.toString();
    }

    private String csvExceptions(AnalyticsModels.Filter filter) {
        StringBuilder builder = new StringBuilder("createdAt,updatedAt,runId,userId,username,organization,rdDepartment,department,workspaceId,agentId,modelId,status\n");
        for (AnalyticsModels.ExceptionDetail row : repository.exceptionDetails(filter).items()) {
            row(builder, row.createdAt(), row.updatedAt(), row.runId(), row.userId(), row.username(), row.organization(),
                    row.rdDepartment(), row.department(), row.workspaceId(), row.agentId(), row.modelId(), row.status());
        }
        return builder.toString();
    }

    private AnalyticsModels.Freshness freshness() {
        return repository.freshness(AnalyticsRollupApplicationService.JOB_NAME, Instant.now().minus(5, ChronoUnit.MINUTES))
                .orElse(new AnalyticsModels.Freshness(null, AnalyticsModels.FreshnessStatus.STALE, "暂无成功统计数据"));
    }

    private Totals totals(List<AnalyticsModels.ActivityRollupRow> rows) {
        Totals totals = new Totals();
        rows.forEach(totals::add);
        return totals;
    }

    private AnalyticsModels.Granularity granularity(String value, Instant start, Instant end) {
        if (value != null && !value.isBlank()) {
            try {
                return AnalyticsModels.Granularity.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "granularity 必须是 hour/day/week/month");
            }
        }
        return Duration.between(start, end).toHours() <= 48
                ? AnalyticsModels.Granularity.HOUR
                : AnalyticsModels.Granularity.DAY;
    }

    private void validateGranularity(Instant start, Instant end, AnalyticsModels.Granularity granularity) {
        Duration duration = Duration.between(start, end);
        long points = switch (granularity) {
            case HOUR -> duration.toHours() + 1;
            case DAY -> duration.toDays() + 1;
            case WEEK -> duration.toDays() / 7 + 1;
            case MONTH -> duration.toDays() / 31 + 1;
        };
        if (granularity == AnalyticsModels.Granularity.HOUR && duration.toHours() > 48) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "超过 48 小时必须使用 day/week/month 粒度");
        }
        if (granularity == AnalyticsModels.Granularity.DAY && duration.toDays() > 180) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "超过 180 天必须使用 week/month 粒度");
        }
        if (points > MAX_POINTS) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "趋势点数超过 500，请提高统计粒度");
        }
    }

    private Instant bucket(AnalyticsModels.ActivityRollupRow row, AnalyticsModels.Granularity granularity) {
        Instant base = row.bucketStart() != null
                ? row.bucketStart()
                : row.activityDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        return switch (granularity) {
            case HOUR -> base.truncatedTo(ChronoUnit.HOURS);
            case DAY -> base.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
            case WEEK -> base.atZone(ZoneOffset.UTC).toLocalDate()
                    .with(java.time.DayOfWeek.MONDAY)
                    .atStartOfDay().toInstant(ZoneOffset.UTC);
            case MONTH -> base.atZone(ZoneOffset.UTC).toLocalDate()
                    .withDayOfMonth(1)
                    .atStartOfDay().toInstant(ZoneOffset.UTC);
        };
    }

    private Comparator<UserTotals> userComparator(String sort) {
        return switch (sort == null ? "active" : sort) {
            case "runs", "runCount" -> Comparator.comparingLong(UserTotals::runCount).reversed();
            case "successRate" -> Comparator.comparing(UserTotals::successRate, nullsLast()).reversed();
            case "satisfactionRate" -> Comparator.comparing(UserTotals::satisfactionRate, nullsLast()).reversed();
            case "diffAcceptanceRate" -> Comparator.comparing(UserTotals::diffAcceptanceRate, nullsLast()).reversed();
            case "cancelRate", "cancellationRate" -> Comparator.comparing(UserTotals::cancellationRate, nullsLast()).reversed();
            case "negativeFeedback" -> Comparator.comparingLong(UserTotals::negativeFeedback).reversed();
            case "tokenUsage", "tokens" -> Comparator.comparingLong(UserTotals::totalTokens).reversed();
            default -> Comparator.comparingLong(UserTotals::activeScore).reversed();
        };
    }

    private Comparator<AnalyticsModels.OrganizationUsageRow> orgComparator(String sort) {
        return switch (sort == null ? "active" : sort) {
            case "runs", "runCount" -> Comparator.comparingLong(AnalyticsModels.OrganizationUsageRow::runCount).reversed();
            case "successRate" -> Comparator.comparing(AnalyticsModels.OrganizationUsageRow::successRate, nullsLast()).reversed();
            case "satisfactionRate" -> Comparator.comparing(AnalyticsModels.OrganizationUsageRow::satisfactionRate, nullsLast()).reversed();
            case "diffAcceptanceRate" -> Comparator.comparing(AnalyticsModels.OrganizationUsageRow::diffAcceptanceRate, nullsLast()).reversed();
            case "negativeFeedback" -> Comparator.comparingLong(AnalyticsModels.OrganizationUsageRow::negativeFeedbackCount).reversed();
            case "tokenUsage", "tokens" -> Comparator.comparingLong(AnalyticsModels.OrganizationUsageRow::totalTokens).reversed();
            default -> Comparator.comparingLong(AnalyticsModels.OrganizationUsageRow::activeUsers).reversed();
        };
    }

    private static Comparator<Double> nullsLast() {
        return Comparator.nullsLast(Double::compareTo);
    }

    private static boolean active(AnalyticsModels.ActivityRollupRow row) {
        return row.userMessageCount() > 0
                || row.runCount() > 0
                || row.positiveFeedbackCount() + row.negativeFeedbackCount() > 0
                || row.diffAcceptedCount() + row.diffRejectedCount() > 0;
    }

    private static Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : numerator * 1.0d / denominator;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void append(StringBuilder builder, Object metric, Object value) {
        row(builder, metric, value);
    }

    private static void row(StringBuilder builder, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(csvValue(values[i]));
        }
        builder.append('\n');
    }

    private static String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    public byte[] exportCsvBytes(AnalyticsModels.Filter filter, String type) {
        return exportCsv(filter, type).getBytes(StandardCharsets.UTF_8);
    }

    private static final class Totals {
        private final Set<String> loginUsers = new java.util.HashSet<>();
        private final Set<String> activeUsers = new java.util.HashSet<>();
        private final Set<String> validUsers = new java.util.HashSet<>();
        private final Map<String, UserTotals> users = new HashMap<>();
        private long sessionCount;
        private long activeSessionCount;
        private long emptySessionCount;
        private long continuousSessionCount;
        private long userMessageCount;
        private long assistantMessageCount;
        private long runCount;
        private long validInteractionCount;
        private long succeededRuns;
        private long failedRuns;
        private long cancelledRuns;
        private long activeTerminations;
        private long positiveFeedback;
        private long negativeFeedback;
        private long diffProposed;
        private long diffAccepted;
        private long diffRejected;
        private long tokensInput;
        private long tokensOutput;
        private long tokensReasoning;
        private long durationTotalMs;
        private long durationRunCount;

        void add(AnalyticsModels.ActivityRollupRow row) {
            UserTotals userTotals = users.computeIfAbsent(row.userId(), ignored -> new UserTotals(row));
            userTotals.add(row);
            if (row.loginCount() > 0) {
                loginUsers.add(row.userId());
            }
            if (active(row)) {
                activeUsers.add(row.userId());
            }
            if (row.validInteractionCount() > 0) {
                validUsers.add(row.userId());
            }
            sessionCount += row.sessionCount();
            activeSessionCount += row.activeSessionCount();
            emptySessionCount += row.emptySessionCount();
            continuousSessionCount += row.continuousSessionCount();
            userMessageCount += row.userMessageCount();
            assistantMessageCount += row.assistantMessageCount();
            runCount += row.runCount();
            validInteractionCount += row.validInteractionCount();
            succeededRuns += row.succeededRunCount();
            failedRuns += row.failedRunCount();
            cancelledRuns += row.cancelledRunCount();
            activeTerminations += row.activeTerminationCount();
            positiveFeedback += row.positiveFeedbackCount();
            negativeFeedback += row.negativeFeedbackCount();
            diffProposed += row.diffProposedCount();
            diffAccepted += row.diffAcceptedCount();
            diffRejected += row.diffRejectedCount();
            tokensInput += row.tokensInput();
            tokensOutput += row.tokensOutput();
            tokensReasoning += row.tokensReasoning();
            durationTotalMs += row.durationTotalMs();
            durationRunCount += row.durationRunCount();
        }

        long tokensTotal() {
            return tokensInput + tokensOutput + tokensReasoning;
        }

        long deepUsers() {
            return users.values().stream().filter(UserTotals::deep).count();
        }

        long sustainedUsers() {
            return users.values().stream().filter(UserTotals::sustained).count();
        }
    }

    private static final class BucketTotals {
        private final Set<String> loginUsers = new java.util.HashSet<>();
        private final Set<String> activeUsers = new java.util.HashSet<>();
        private long sessionCount;
        private long activeSessionCount;
        private long userMessageCount;
        private long assistantMessageCount;
        private long runCount;
        private long succeededRuns;
        private long failedRuns;
        private long cancelledRuns;
        private long positiveFeedback;
        private long negativeFeedback;
        private long diffProposed;
        private long diffAccepted;
        private long diffRejected;
        private long totalTokens;

        void add(AnalyticsModels.ActivityRollupRow row) {
            if (row.loginCount() > 0) {
                loginUsers.add(row.userId());
            }
            if (active(row)) {
                activeUsers.add(row.userId());
            }
            sessionCount += row.sessionCount();
            activeSessionCount += row.activeSessionCount();
            userMessageCount += row.userMessageCount();
            assistantMessageCount += row.assistantMessageCount();
            runCount += row.runCount();
            succeededRuns += row.succeededRunCount();
            failedRuns += row.failedRunCount();
            cancelledRuns += row.cancelledRunCount();
            positiveFeedback += row.positiveFeedbackCount();
            negativeFeedback += row.negativeFeedbackCount();
            diffProposed += row.diffProposedCount();
            diffAccepted += row.diffAcceptedCount();
            diffRejected += row.diffRejectedCount();
            totalTokens += row.tokensTotal();
        }

        AnalyticsModels.TimeSeriesPoint toPoint(Instant bucket) {
            return new AnalyticsModels.TimeSeriesPoint(
                    bucket,
                    loginUsers.size(),
                    activeUsers.size(),
                    sessionCount,
                    activeSessionCount,
                    userMessageCount,
                    assistantMessageCount,
                    runCount,
                    succeededRuns,
                    failedRuns,
                    cancelledRuns,
                    positiveFeedback,
                    negativeFeedback,
                    diffAccepted,
                    diffRejected,
                    totalTokens,
                    ratio(positiveFeedback, positiveFeedback + negativeFeedback),
                    ratio(diffAccepted, diffProposed),
                    ratio(cancelledRuns, runCount));
        }

        AnalyticsModels.PeakPoint toPeak(Instant bucket) {
            return new AnalyticsModels.PeakPoint(
                    bucket,
                    activeUsers.size(),
                    runCount,
                    userMessageCount,
                    ratio(positiveFeedback, positiveFeedback + negativeFeedback),
                    ratio(cancelledRuns, runCount),
                    totalTokens);
        }
    }

    private static final class HeatmapTotals {
        private final int dayOfWeek;
        private final int hour;
        private long activeUsers;
        private long runCount;
        private long userMessageCount;

        private HeatmapTotals(int dayOfWeek, int hour) {
            this.dayOfWeek = dayOfWeek;
            this.hour = hour;
        }

        void add(BucketTotals totals) {
            activeUsers += totals.activeUsers.size();
            runCount += totals.runCount;
            userMessageCount += totals.userMessageCount;
        }

        AnalyticsModels.HeatmapPoint toPoint() {
            return new AnalyticsModels.HeatmapPoint(dayOfWeek, hour, activeUsers, runCount, userMessageCount);
        }
    }

    private static final class UserTotals {
        private final String userId;
        private String username;
        private String organization;
        private String rdDepartment;
        private String department;
        private final Set<LocalDate> activeDates = new java.util.HashSet<>();
        private long loginCount;
        private long sessionCount;
        private long activeSessionCount;
        private long userMessageCount;
        private long runCount;
        private long succeededRuns;
        private long failedRuns;
        private long cancelledRuns;
        private long positiveFeedback;
        private long negativeFeedback;
        private long diffProposed;
        private long diffAccepted;
        private long diffRejected;
        private long totalTokens;
        private Instant lastActivityAt;

        private UserTotals(AnalyticsModels.ActivityRollupRow row) {
            this.userId = row.userId();
            this.username = row.username();
            this.organization = row.organization();
            this.rdDepartment = row.rdDepartment();
            this.department = row.department();
        }

        void add(AnalyticsModels.ActivityRollupRow row) {
            if ((username == null || username.isBlank()) && row.username() != null) {
                username = row.username();
            }
            organization = valueOr(organization, row.organization());
            rdDepartment = valueOr(rdDepartment, row.rdDepartment());
            department = valueOr(department, row.department());
            loginCount += row.loginCount();
            sessionCount += row.sessionCount();
            activeSessionCount += row.activeSessionCount();
            userMessageCount += row.userMessageCount();
            runCount += row.runCount();
            succeededRuns += row.succeededRunCount();
            failedRuns += row.failedRunCount();
            cancelledRuns += row.cancelledRunCount();
            positiveFeedback += row.positiveFeedbackCount();
            negativeFeedback += row.negativeFeedbackCount();
            diffProposed += row.diffProposedCount();
            diffAccepted += row.diffAcceptedCount();
            diffRejected += row.diffRejectedCount();
            totalTokens += row.tokensTotal();
            if (active(row)) {
                activeDates.add(activityDate(row));
            }
            if (row.lastActivityAt() != null && (lastActivityAt == null || row.lastActivityAt().isAfter(lastActivityAt))) {
                lastActivityAt = row.lastActivityAt();
            }
        }

        AnalyticsModels.UserUsageRow toRow() {
            return new AnalyticsModels.UserUsageRow(
                    userId,
                    username,
                    organization,
                    rdDepartment,
                    department,
                    loginCount,
                    sessionCount,
                    activeSessionCount,
                    userMessageCount,
                    runCount,
                    succeededRuns,
                    failedRuns,
                    cancelledRuns,
                    positiveFeedback,
                    negativeFeedback,
                    diffAccepted,
                    diffRejected,
                    totalTokens,
                    successRate(),
                    satisfactionRate(),
                    diffAcceptanceRate(),
                    lastActivityAt);
        }

        boolean deep() {
            return runCount >= 3 || userMessageCount >= 5 || diffAccepted > 0 || positiveFeedback + negativeFeedback > 0;
        }

        boolean sustained() {
            return activeDates.size() >= 2;
        }

        long activeScore() {
            return userMessageCount + runCount + positiveFeedback + negativeFeedback + diffAccepted + diffRejected;
        }

        long runCount() {
            return runCount;
        }

        long negativeFeedback() {
            return negativeFeedback;
        }

        long totalTokens() {
            return totalTokens;
        }

        Double successRate() {
            return ratio(succeededRuns, runCount);
        }

        Double satisfactionRate() {
            return ratio(positiveFeedback, positiveFeedback + negativeFeedback);
        }

        Double diffAcceptanceRate() {
            return ratio(diffAccepted, diffProposed);
        }

        Double cancellationRate() {
            return ratio(cancelledRuns, runCount);
        }

        private static LocalDate activityDate(AnalyticsModels.ActivityRollupRow row) {
            if (row.activityDate() != null) {
                return row.activityDate();
            }
            return row.bucketStart().atZone(ZoneOffset.UTC).toLocalDate();
        }

        private static String valueOr(String current, String candidate) {
            if (current == null || current.isBlank() || "未归属".equals(current)) {
                return candidate == null || candidate.isBlank() ? "未归属" : candidate;
            }
            return current;
        }
    }
}
