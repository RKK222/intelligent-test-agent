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
import java.time.ZoneId;
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

    public static final ZoneId ANALYTICS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final int MAX_TOP_N = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_POINTS = 500;
    private static final int DEFAULT_RANGE_DAYS = 30;

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
        Instant start = startTime == null ? end.minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS) : startTime;
        if (!start.isBefore(end)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "startTime 必须早于 endTime");
        }
        Duration range = Duration.between(start, end);
        long days = range.toDays();
        if (range.compareTo(Duration.ofDays(366)) > 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "查询时间范围不能超过 366 天");
        }
        if (blankToNull(agentId) != null || blankToNull(model) != null || blankToNull(workspaceId) != null) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "agentId、model、workspaceId 已停止支持，请使用能力运营页查看 Agent/Skill/Tool");
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
                null,
                null,
                null,
                resolvedTopN,
                resolvedPage,
                resolvedPageSize,
                blankToNull(sort));
    }

    public AnalyticsModels.FilterOptions filterOptions(AnalyticsModels.Filter filter) {
        return new AnalyticsModels.FilterOptions(
                repository.organizations(),
                repository.rdDepartments(filter.organization()),
                repository.departments(filter.organization(), filter.rdDepartment()),
                freshness());
    }

    /** 总用户取 ClickHouse 最新用户维度快照，活跃与深度用户由所选时间内的行为事实判定。 */
    public AnalyticsModels.Funnel funnel(AnalyticsModels.Filter filter) {
        Map<String, FunnelUser> users = new LinkedHashMap<>();
        for (AnalyticsModels.ActivityRollupRow row : repository.queryRollups(filter)) {
            users.computeIfAbsent(row.userId(), ignored -> new FunnelUser()).add(row);
        }
        long total = repository.countRegisteredUsers(filter);
        long active = users.values().stream().filter(FunnelUser::active).count();
        long deep = users.values().stream().filter(FunnelUser::deep).count();
        return new AnalyticsModels.Funnel(
                total,
                active,
                deep,
                ratio(active, total),
                ratio(deep, active),
                "活跃用户：所选时间内至少发送 1 条用户消息",
                "深度用户：活跃用户中，至少 2 个自然日有使用且累计至少 5 条用户消息",
                freshness());
    }

    /**
     * 热力图按上海自然日逐小时补零，避免把 UTC 日期直接展示给运营人员。
     */
    public AnalyticsModels.HourlyHeatmap hourlyHeatmap(
            AnalyticsModels.Filter filter,
            AnalyticsModels.HeatmapMetric metric) {
        if (Duration.between(filter.startTime(), filter.endTime()).compareTo(Duration.ofDays(90)) > 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "小时热力图时间范围不能超过 90 天");
        }
        LocalDate firstDate = filter.startTime().atZone(ANALYTICS_ZONE).toLocalDate();
        LocalDate lastDate = filter.endTime().minusNanos(1).atZone(ANALYTICS_ZONE).toLocalDate();
        List<LocalDate> dates = firstDate.datesUntil(lastDate.plusDays(1)).toList();
        Map<String, Long> values = new HashMap<>();
        AnalyticsModels.Filter hourly = hourlyFilter(filter);
        for (AnalyticsModels.ActivityRollupRow row : repository.queryRollups(hourly)) {
            if (row.bucketStart() == null) {
                continue;
            }
            var local = row.bucketStart().atZone(ANALYTICS_ZONE);
            long value = switch (metric) {
                case USER_MESSAGES -> row.userMessageCount();
                case PRIMARY_TOKENS -> row.tokensTotal();
                case CACHE_TOKENS -> row.tokensCacheRead() + row.tokensCacheWrite();
            };
            values.merge(local.toLocalDate() + ":" + local.getHour(), value, Long::sum);
        }
        List<AnalyticsModels.HourlyHeatmapPoint> points = new ArrayList<>(dates.size() * 24);
        for (LocalDate date : dates) {
            for (int hour = 0; hour < 24; hour++) {
                points.add(new AnalyticsModels.HourlyHeatmapPoint(
                        date, hour, values.getOrDefault(date + ":" + hour, 0L)));
            }
        }
        return new AnalyticsModels.HourlyHeatmap(metric, dates, points, freshness());
    }

    /**
     * 日人均 Token 以“用户在某个自然日确实产生过 Token”的人天为分母。
     */
    public AnalyticsModels.TokenOperations tokenOperations(AnalyticsModels.Filter filter) {
        Map<String, TokenUserTotals> users = new LinkedHashMap<>();
        Map<LocalDate, TokenDailyTotals> daily = new TreeMap<>();
        Set<String> activeUsers = new java.util.HashSet<>();
        for (AnalyticsModels.ActivityRollupRow row : repository.queryRollups(filter)) {
            if (row.userMessageCount() > 0) {
                activeUsers.add(row.userId());
            }
            LocalDate date = activityDate(row);
            if (tokenActive(row)) {
                users.computeIfAbsent(row.userId(), ignored -> new TokenUserTotals(row)).add(row, date);
                daily.computeIfAbsent(date, ignored -> new TokenDailyTotals()).add(row);
            }
        }
        long total = users.values().stream().mapToLong(TokenUserTotals::totalTokens).sum();
        long primary = users.values().stream().mapToLong(TokenUserTotals::primaryTokens).sum();
        long cacheRead = users.values().stream().mapToLong(TokenUserTotals::cacheReadTokens).sum();
        long cacheWrite = users.values().stream().mapToLong(TokenUserTotals::cacheWriteTokens).sum();
        long personDays = users.values().stream().mapToLong(TokenUserTotals::tokenDays).sum();
        long repeatUsers = users.values().stream().filter(user -> user.tokenDays() >= 2).count();
        List<TokenUserTotals> ordered = users.values().stream()
                .sorted(Comparator.comparingLong(TokenUserTotals::totalTokens).reversed())
                .toList();
        double[] quartiles = quartiles(ordered.stream().mapToDouble(TokenUserTotals::tokensPerTokenDay).sorted().toArray());
        return new AnalyticsModels.TokenOperations(
                total,
                primary,
                cacheRead,
                cacheWrite,
                users.size(),
                activeUsers.size(),
                ratio(users.size(), activeUsers.size()),
                personDays,
                ratio(total, personDays),
                repeatUsers,
                ratio(repeatUsers, users.size()),
                daily.entrySet().stream().map(entry -> entry.getValue().toRow(entry.getKey())).toList(),
                ordered.stream().limit(filter.topN()).map(user -> user.toRow(quartiles)).toList(),
                freshness());
    }

    public AnalyticsModels.Capabilities capabilities(AnalyticsModels.Filter filter) {
        long activeUsers = repository.queryRollups(filter).stream()
                .filter(row -> row.userMessageCount() > 0)
                .map(AnalyticsModels.ActivityRollupRow::userId)
                .distinct()
                .count();
        List<AnalyticsModels.CapabilityUsage> rows = repository.capabilityUsage(filter).stream()
                .map(row -> new AnalyticsModels.CapabilityUsage(
                        row.type(), row.name(), row.invocationCount(), row.userCount(),
                        ratio(row.userCount(), activeUsers), row.succeededCount(), row.failedCount(),
                        row.cancelledCount(), row.incompleteCount()))
                .sorted(Comparator.comparingLong(AnalyticsModels.CapabilityUsage::userCount).reversed()
                        .thenComparing(Comparator.comparingLong(AnalyticsModels.CapabilityUsage::invocationCount).reversed()))
                .toList();
        AnalyticsModels.CapabilityCoverage coverage = repository.capabilityCoverage(filter)
                .orElse(new AnalyticsModels.CapabilityCoverage("LEGACY_RUNEVENT", null, null, 0D));
        return new AnalyticsModels.Capabilities(
                activeUsers,
                rows,
                freshness(),
                coverage.source(),
                coverage.coverageStartAt(),
                coverage.completeThrough(),
                coverage.rolloutCompleteness());
    }

    private AnalyticsModels.Filter hourlyFilter(AnalyticsModels.Filter filter) {
        return new AnalyticsModels.Filter(
                filter.startTime(), filter.endTime(), AnalyticsModels.Granularity.HOUR,
                filter.organization(), filter.rdDepartment(), filter.department(), filter.userKeyword(),
                null, null, null, filter.topN(), filter.page(), filter.pageSize(), filter.sort());
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
        fillMissingTimeBuckets(grouped, filter);
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
                filter.userKeyword(),
                null,
                null,
                null,
                Math.min(filter.topN(), 5),
                filter.page(),
                filter.pageSize(),
                filter.sort());
        Map<Instant, BucketTotals> grouped = new TreeMap<>();
        for (AnalyticsModels.ActivityRollupRow row : repository.queryRollups(hourly)) {
            grouped.computeIfAbsent(bucket(row, AnalyticsModels.Granularity.HOUR), ignored -> new BucketTotals()).add(row);
        }
        List<AnalyticsModels.PeakPoint> peakPeriods = grouped.entrySet().stream()
                .map(entry -> entry.getValue().toPeak(entry.getKey()))
                .sorted(Comparator.comparingLong(AnalyticsModels.PeakPoint::activeUsers).reversed()
                        .thenComparing(Comparator.comparingLong(AnalyticsModels.PeakPoint::runCount).reversed()))
                .limit(hourly.topN())
                .toList();
        Map<String, HeatmapTotals> heatmap = new LinkedHashMap<>();
        // 固定补齐周一至周日的 24 小时，避免无活动时段被省略后破坏热力图坐标语义。
        for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
            for (int hour = 0; hour < 24; hour++) {
                heatmap.put(dayOfWeek + ":" + hour, new HeatmapTotals(dayOfWeek, hour));
            }
        }
        grouped.forEach((bucket, totals) -> {
            int dayOfWeek = bucket.atZone(ANALYTICS_ZONE).getDayOfWeek().getValue();
            int hour = bucket.atZone(ANALYTICS_ZONE).getHour();
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
            case "funnel" -> csvFunnel(filter);
            case "token-operations" -> csvTokenOperations(filter);
            case "capabilities" -> csvCapabilities(filter);
            default -> csvOverview(filter);
        };
    }

    private String csvFunnel(AnalyticsModels.Filter filter) {
        AnalyticsModels.Funnel funnel = funnel(filter);
        StringBuilder builder = new StringBuilder("stage,userCount,conversionRate,definition\n");
        row(builder, "total", funnel.totalUsers(), 1, "当前全部平台用户");
        row(builder, "active", funnel.activeUsers(), funnel.activeRate(), funnel.activeDefinition());
        row(builder, "deep", funnel.deepUsers(), funnel.deepRate(), funnel.deepDefinition());
        return builder.toString();
    }

    private String csvTokenOperations(AnalyticsModels.Filter filter) {
        AnalyticsModels.TokenOperations result = tokenOperations(filter);
        StringBuilder builder = new StringBuilder(
                "userId,username,organization,rdDepartment,department,totalTokens,primaryTokens,cacheReadTokens,cacheWriteTokens,tokenDays,tokensPerTokenDay,intensityBand\n");
        for (AnalyticsModels.TokenUserRow row : result.users()) {
            row(builder, row.userId(), row.username(), row.organization(), row.rdDepartment(), row.department(),
                    row.totalTokens(), row.primaryTokens(), row.cacheReadTokens(), row.cacheWriteTokens(), row.tokenDays(),
                    row.tokensPerTokenDay(), row.intensityBand());
        }
        return builder.toString();
    }

    private String csvCapabilities(AnalyticsModels.Filter filter) {
        StringBuilder builder = new StringBuilder(
                "type,name,usageRate,userCount,invocationCount,succeededCount,failedCount,cancelledCount,incompleteCount\n");
        for (AnalyticsModels.CapabilityUsage row : capabilities(filter).rows()) {
            row(builder, row.type(), row.name(), row.usageRate(), row.userCount(), row.invocationCount(),
                    row.succeededCount(), row.failedCount(), row.cancelledCount(), row.incompleteCount());
        }
        return builder.toString();
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
        Duration duration = Duration.between(start, end);
        if (duration.toHours() <= 48) {
            return AnalyticsModels.Granularity.HOUR;
        }
        return duration.toDays() <= 90
                ? AnalyticsModels.Granularity.DAY
                : AnalyticsModels.Granularity.WEEK;
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
        if (granularity == AnalyticsModels.Granularity.DAY && duration.toDays() > 90) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "超过 90 天必须使用 week/month 粒度");
        }
        if (points > MAX_POINTS) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "趋势点数超过 500，请提高统计粒度");
        }
    }

    private Instant bucket(AnalyticsModels.ActivityRollupRow row, AnalyticsModels.Granularity granularity) {
        Instant base = row.bucketStart() != null
                ? row.bucketStart()
                : row.activityDate().atStartOfDay(ANALYTICS_ZONE).toInstant();
        return bucket(base, granularity);
    }

    private Instant bucket(Instant base, AnalyticsModels.Granularity granularity) {
        return switch (granularity) {
            case HOUR -> base.truncatedTo(ChronoUnit.HOURS);
            case DAY -> base.atZone(ANALYTICS_ZONE).toLocalDate().atStartOfDay(ANALYTICS_ZONE).toInstant();
            case WEEK -> base.atZone(ANALYTICS_ZONE).toLocalDate()
                    .with(java.time.DayOfWeek.MONDAY)
                    .atStartOfDay(ANALYTICS_ZONE).toInstant();
            case MONTH -> base.atZone(ANALYTICS_ZONE).toLocalDate()
                    .withDayOfMonth(1)
                    .atStartOfDay(ANALYTICS_ZONE).toInstant();
        };
    }

    /**
     * 在已校验的最多 500 个时间桶内补零，使趋势轴反映完整查询范围而不是仅展示有活动的日期。
     */
    private void fillMissingTimeBuckets(Map<Instant, BucketTotals> grouped, AnalyticsModels.Filter filter) {
        Instant cursor = bucket(filter.startTime(), filter.granularity());
        while (cursor.isBefore(filter.endTime())) {
            grouped.computeIfAbsent(cursor, ignored -> new BucketTotals());
            cursor = nextBucket(cursor, filter.granularity());
        }
    }

    private Instant nextBucket(Instant current, AnalyticsModels.Granularity granularity) {
        return switch (granularity) {
            case HOUR -> current.plus(1, ChronoUnit.HOURS);
            case DAY -> current.plus(1, ChronoUnit.DAYS);
            case WEEK -> current.plus(7, ChronoUnit.DAYS);
            case MONTH -> current.atZone(ANALYTICS_ZONE).toLocalDate()
                    .plusMonths(1)
                    .withDayOfMonth(1)
                    .atStartOfDay(ANALYTICS_ZONE).toInstant();
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
        return row.userMessageCount() > 0;
    }

    private static Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : numerator * 1.0d / denominator;
    }

    private static boolean tokenActive(AnalyticsModels.ActivityRollupRow row) {
        return row.tokensTotal() + row.tokensCacheRead() + row.tokensCacheWrite() > 0;
    }

    private static LocalDate activityDate(AnalyticsModels.ActivityRollupRow row) {
        return row.activityDate() != null
                ? row.activityDate()
                : row.bucketStart().atZone(ANALYTICS_ZONE).toLocalDate();
    }

    private static double[] quartiles(double[] values) {
        if (values.length == 0) {
            return new double[] {0, 0, 0};
        }
        return new double[] {percentile(values, 0.25d), percentile(values, 0.5d), percentile(values, 0.75d)};
    }

    private static double percentile(double[] values, double percentile) {
        double index = percentile * (values.length - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return values[lower];
        }
        return values[lower] + (values[upper] - values[lower]) * (index - lower);
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

    // 以下三个格式化方法与前端 AnalyticsManagementPanel 的展示口径保持一致，确保 xlsx 与网页内容一致
    private static String formatRate(Double value) {
        return value == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f%%", value * 100);
    }

    private static String formatNumber(Double value) {
        if (value == null) {
            return "-";
        }
        String formatted = String.format(java.util.Locale.ROOT, "%,.2f", value);
        if (formatted.endsWith(".00")) {
            formatted = formatted.substring(0, formatted.length() - 3);
        } else if (formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private static String formatDuration(Long valueMs) {
        if (valueMs == null) {
            return "-";
        }
        return valueMs < 1000 ? valueMs + "ms" : String.format(java.util.Locale.ROOT, "%.1fs", valueMs / 1000.0);
    }

    public byte[] exportCsvBytes(AnalyticsModels.Filter filter, String type) {
        return exportCsv(filter, type).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 一次导出所有运营分析 Tab 为多 Sheet xlsx，Sheet 名和列头均用中文且与网页表格一致。
     * 复用各 Tab 现有查询方法，避免重复实现数据获取逻辑。
     */
    public byte[] exportAllXlsx(AnalyticsModels.Filter filter) {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.CellStyle sectionStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font sectionFont = workbook.createFont();
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 13);
            sectionStyle.setFont(sectionFont);

            buildOverviewSheet(workbook, filter, headerStyle, sectionStyle);
            buildUsersSheet(workbook, filter, headerStyle);
            buildTokenSheet(workbook, filter, headerStyle, sectionStyle);
            buildCapabilitiesSheet(workbook, filter, headerStyle);
            buildOrganizationsSheet(workbook, filter, headerStyle);
            buildFeedbackSheet(workbook, filter, headerStyle);
            buildExceptionsSheet(workbook, filter, headerStyle);

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (java.io.IOException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "导出 xlsx 失败", Map.of(), exception);
        }
    }

    // 使用总览 Sheet：KPI 指标卡 + 用户使用漏斗
    private void buildOverviewSheet(
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
            AnalyticsModels.Filter filter,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle sectionStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("使用总览");
        int rowIdx = 0;
        AnalyticsModels.Overview overview = overview(filter);
        AnalyticsModels.Funnel funnel = funnel(filter);

        org.apache.poi.ss.usermodel.Row sectionRow = sheet.createRow(rowIdx++);
        sectionRow.createCell(0).setCellValue("核心指标");
        sectionRow.getCell(0).setCellStyle(sectionStyle);
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowIdx++);
        headerRow.createCell(0).setCellValue("指标");
        headerRow.createCell(1).setCellValue("数值");
        for (int i = 0; i <= 1; i++) {
            headerRow.getCell(i).setCellStyle(headerStyle);
        }
        rowIdx = writeOverviewKpiRows(sheet, rowIdx, overview);

        org.apache.poi.ss.usermodel.Row funnelSection = sheet.createRow(rowIdx++);
        funnelSection.createCell(0).setCellValue("用户使用漏斗");
        funnelSection.getCell(0).setCellStyle(sectionStyle);
        org.apache.poi.ss.usermodel.Row funnelHeader = sheet.createRow(rowIdx++);
        String[] funnelHeaders = {"阶段", "用户数", "转化率", "定义"};
        for (int i = 0; i < funnelHeaders.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = funnelHeader.createCell(i);
            cell.setCellValue(funnelHeaders[i]);
            cell.setCellStyle(headerStyle);
        }
        writeFunnelRow(sheet.createRow(rowIdx++), "总用户数", funnel.totalUsers(), null, "当前全部平台用户");
        writeFunnelRow(sheet.createRow(rowIdx++), "活跃用户数", funnel.activeUsers(), funnel.activeRate(), funnel.activeDefinition());
        writeFunnelRow(sheet.createRow(rowIdx++), "深度用户数", funnel.deepUsers(), funnel.deepRate(), funnel.deepDefinition());
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
        sheet.autoSizeColumn(3);
    }

    private int writeOverviewKpiRows(org.apache.poi.ss.usermodel.Sheet sheet, int rowIdx, AnalyticsModels.Overview overview) {
        rowIdx = writeMetricRow(sheet, rowIdx, "注册用户", overview.registeredUsers());
        rowIdx = writeMetricRow(sheet, rowIdx, "启用用户", overview.enabledUsers());
        rowIdx = writeMetricRow(sheet, rowIdx, "登录用户", overview.loginUsers());
        rowIdx = writeMetricRow(sheet, rowIdx, "活跃用户", overview.activeUsers());
        rowIdx = writeMetricRow(sheet, rowIdx, "有效用户", overview.validUsers());
        rowIdx = writeMetricRow(sheet, rowIdx, "深度用户", overview.deepUsers());
        rowIdx = writeMetricRow(sheet, rowIdx, "会话总数", overview.sessionCount());
        rowIdx = writeMetricRow(sheet, rowIdx, "活跃会话", overview.activeSessionCount());
        rowIdx = writeMetricRow(sheet, rowIdx, "用户消息", overview.userMessageCount());
        rowIdx = writeMetricRow(sheet, rowIdx, "AI 回复", overview.assistantMessageCount());
        rowIdx = writeMetricRow(sheet, rowIdx, "Run 启动", overview.runCount());
        rowIdx = writeMetricRow(sheet, rowIdx, "成功率", formatRate(overview.successRate()));
        rowIdx = writeMetricRow(sheet, rowIdx, "满意率", formatRate(overview.satisfactionRate()));
        rowIdx = writeMetricRow(sheet, rowIdx, "Diff 采纳率", formatRate(overview.diffAcceptanceRate()));
        rowIdx = writeMetricRow(sheet, rowIdx, "p95 耗时", formatDuration(overview.p95DurationMs()));
        rowIdx = writeMetricRow(sheet, rowIdx, "平均耗时", formatDuration(overview.averageDurationMs()));
        rowIdx = writeMetricRow(sheet, rowIdx, "主 Token 使用量", overview.totalTokens());
        return rowIdx;
    }

    // 用户运营 Sheet
    private void buildUsersSheet(
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
            AnalyticsModels.Filter filter,
            org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("用户运营");
        String[] headers = {"用户", "机构", "研发部", "部门", "登录", "会话", "消息", "Run", "成功率", "满意率", "Token"};
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        int rowIdx = 1;
        for (AnalyticsModels.UserUsageRow row : users(filter).items()) {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(displayName(row.username(), row.userId()));
            r.createCell(1).setCellValue(orDash(row.organization()));
            r.createCell(2).setCellValue(orDash(row.rdDepartment()));
            r.createCell(3).setCellValue(orDash(row.department()));
            r.createCell(4).setCellValue(row.loginCount());
            r.createCell(5).setCellValue(row.activeSessionCount());
            r.createCell(6).setCellValue(row.userMessageCount());
            r.createCell(7).setCellValue(row.runCount());
            r.createCell(8).setCellValue(formatRate(row.successRate()));
            r.createCell(9).setCellValue(formatRate(row.satisfactionRate()));
            r.createCell(10).setCellValue(row.totalTokens());
        }
        autoSizeColumns(sheet, headers.length);
    }

    // Token 运营 Sheet：每日 Token + 用户排行两段
    private void buildTokenSheet(
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
            AnalyticsModels.Filter filter,
            org.apache.poi.ss.usermodel.CellStyle headerStyle,
            org.apache.poi.ss.usermodel.CellStyle sectionStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Token运营");
        AnalyticsModels.TokenOperations token = tokenOperations(filter);
        int rowIdx = 0;
        org.apache.poi.ss.usermodel.Row section1 = sheet.createRow(rowIdx++);
        section1.createCell(0).setCellValue("每日 Token 使用");
        section1.getCell(0).setCellStyle(sectionStyle);
        String[] dailyHeaders = {"日期", "使用用户", "总 Token", "日人均", "主 Token", "缓存读", "缓存写"};
        org.apache.poi.ss.usermodel.Row dailyHeader = sheet.createRow(rowIdx++);
        for (int i = 0; i < dailyHeaders.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = dailyHeader.createCell(i);
            cell.setCellValue(dailyHeaders[i]);
            cell.setCellStyle(headerStyle);
        }
        for (AnalyticsModels.TokenDailyPoint point : token.daily()) {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(point.date() == null ? "" : point.date().toString());
            r.createCell(1).setCellValue(point.tokenUsers());
            r.createCell(2).setCellValue(point.totalTokens());
            r.createCell(3).setCellValue(formatNumber(point.tokensPerUser()));
            r.createCell(4).setCellValue(point.primaryTokens());
            r.createCell(5).setCellValue(point.cacheReadTokens());
            r.createCell(6).setCellValue(point.cacheWriteTokens());
        }

        rowIdx++;
        org.apache.poi.ss.usermodel.Row section2 = sheet.createRow(rowIdx++);
        section2.createCell(0).setCellValue("用户使用排行");
        section2.getCell(0).setCellStyle(sectionStyle);
        String[] userHeaders = {"用户", "使用强度", "Token 日", "总 Token", "Token 日均"};
        org.apache.poi.ss.usermodel.Row userHeader = sheet.createRow(rowIdx++);
        for (int i = 0; i < userHeaders.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = userHeader.createCell(i);
            cell.setCellValue(userHeaders[i]);
            cell.setCellStyle(headerStyle);
        }
        for (AnalyticsModels.TokenUserRow row : token.users()) {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(displayName(row.username(), row.userId()));
            r.createCell(1).setCellValue(orDash(row.intensityBand()));
            r.createCell(2).setCellValue(row.tokenDays());
            r.createCell(3).setCellValue(row.totalTokens());
            r.createCell(4).setCellValue(formatNumber(row.tokensPerTokenDay()));
        }
        autoSizeColumns(sheet, dailyHeaders.length);
    }

    // 能力使用 Sheet
    private void buildCapabilitiesSheet(
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
            AnalyticsModels.Filter filter,
            org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("能力使用");
        String[] headers = {"类型", "名称", "使用率", "使用用户", "调用次数", "成功", "失败", "取消", "未完成"};
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        int rowIdx = 1;
        for (AnalyticsModels.CapabilityUsage row : capabilities(filter).rows()) {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(capabilityLabel(row.type()));
            r.createCell(1).setCellValue(row.name());
            r.createCell(2).setCellValue(formatRate(row.usageRate()));
            r.createCell(3).setCellValue(row.userCount());
            r.createCell(4).setCellValue(row.invocationCount());
            r.createCell(5).setCellValue(row.succeededCount());
            r.createCell(6).setCellValue(row.failedCount());
            r.createCell(7).setCellValue(row.cancelledCount());
            r.createCell(8).setCellValue(row.incompleteCount());
        }
        autoSizeColumns(sheet, headers.length);
    }

    // 组织分析 Sheet
    private void buildOrganizationsSheet(
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
            AnalyticsModels.Filter filter,
            org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("组织分析");
        String[] headers = {"维度", "名称", "登录用户", "活跃用户", "深度用户", "Run", "成功率", "满意率", "Token"};
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        int rowIdx = 1;
        for (AnalyticsModels.OrganizationUsageRow row : organizations(filter, "department")) {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(row.dimension());
            r.createCell(1).setCellValue(row.name());
            r.createCell(2).setCellValue(row.loginUsers());
            r.createCell(3).setCellValue(row.activeUsers());
            r.createCell(4).setCellValue(row.deepUsers());
            r.createCell(5).setCellValue(row.runCount());
            r.createCell(6).setCellValue(formatRate(row.successRate()));
            r.createCell(7).setCellValue(formatRate(row.satisfactionRate()));
            r.createCell(8).setCellValue(row.totalTokens());
        }
        autoSizeColumns(sheet, headers.length);
    }

    // 满意度 Sheet
    private void buildFeedbackSheet(
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
            AnalyticsModels.Filter filter,
            org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("满意度");
        String[] headers = {"时间", "用户", "组织", "会话", "Run", "反馈", "原因", "备注"};
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        int rowIdx = 1;
        for (AnalyticsModels.FeedbackDetail row : feedbackDetails(filter).items()) {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(formatInstant(row.createdAt()));
            r.createCell(1).setCellValue(displayName(row.username(), row.userId()));
            r.createCell(2).setCellValue(joinOrg(row.organization(), row.rdDepartment(), row.department()));
            r.createCell(3).setCellValue(row.sessionId());
            r.createCell(4).setCellValue(orDash(row.runId()));
            r.createCell(5).setCellValue(ratingLabel(row.rating()));
            r.createCell(6).setCellValue(orDash(reasonLabel(row.reasonCode())));
            r.createCell(7).setCellValue(orDash(row.comment()));
        }
        autoSizeColumns(sheet, headers.length);
    }

    // 异常 Run Sheet
    private void buildExceptionsSheet(
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
            AnalyticsModels.Filter filter,
            org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("异常Run");
        String[] headers = {"时间", "Run", "用户", "组织", "状态"};
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        int rowIdx = 1;
        for (AnalyticsModels.ExceptionDetail row : exceptionDetails(filter).items()) {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(formatInstant(row.updatedAt()));
            r.createCell(1).setCellValue(row.runId());
            r.createCell(2).setCellValue(displayName(row.username(), row.userId()));
            r.createCell(3).setCellValue(joinOrg(row.organization(), row.rdDepartment(), row.department()));
            r.createCell(4).setCellValue(row.status());
        }
        autoSizeColumns(sheet, headers.length);
    }

    private int writeMetricRow(org.apache.poi.ss.usermodel.Sheet sheet, int rowIdx, String metric, Object value) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(metric);
        org.apache.poi.ss.usermodel.Cell valueCell = row.createCell(1);
        if (value instanceof Number number) {
            valueCell.setCellValue(number.doubleValue());
        } else {
            valueCell.setCellValue(value == null ? "" : value.toString());
        }
        return rowIdx + 1;
    }

    private void writeFunnelRow(org.apache.poi.ss.usermodel.Row row, String stage, long userCount, Double rate, String definition) {
        row.createCell(0).setCellValue(stage);
        row.createCell(1).setCellValue(userCount);
        row.createCell(2).setCellValue(rate == null ? "-" : formatRate(rate));
        row.createCell(3).setCellValue(definition == null ? "" : definition);
    }

    private void autoSizeColumns(org.apache.poi.ss.usermodel.Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String capabilityLabel(String type) {
        return "AGENT".equals(type) ? "Agent" : "SKILL".equals(type) ? "Skill" : "TOOL".equals(type) ? "Tool" : type;
    }

    private String ratingLabel(com.enterprise.testagent.domain.analytics.AiMessageFeedbackRating rating) {
        return rating == com.enterprise.testagent.domain.analytics.AiMessageFeedbackRating.POSITIVE ? "满意" : "不满意";
    }

    private String reasonLabel(com.enterprise.testagent.domain.analytics.AiMessageFeedbackReasonCode reasonCode) {
        return reasonCode == null ? null : reasonCode.name();
    }

    private String displayName(String username, String userId) {
        return username != null && !username.isBlank() ? username : userId;
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String joinOrg(String organization, String rdDepartment, String department) {
        List<String> parts = new ArrayList<>();
        if (organization != null && !organization.isBlank()) {
            parts.add(organization);
        }
        if (rdDepartment != null && !rdDepartment.isBlank()) {
            parts.add(rdDepartment);
        }
        if (department != null && !department.isBlank()) {
            parts.add(department);
        }
        return parts.isEmpty() ? "-" : String.join(" / ", parts);
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : instant.atZone(ANALYTICS_ZONE).toString();
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

    private static final class FunnelUser {
        private final Set<LocalDate> messageDates = new java.util.HashSet<>();
        private long userMessageCount;

        void add(AnalyticsModels.ActivityRollupRow row) {
            userMessageCount += row.userMessageCount();
            if (row.userMessageCount() > 0) {
                messageDates.add(activityDate(row));
            }
        }

        boolean active() {
            return userMessageCount > 0;
        }

        boolean deep() {
            return active() && messageDates.size() >= 2 && userMessageCount >= 5;
        }
    }

    private static final class TokenDailyTotals {
        private final Set<String> users = new java.util.HashSet<>();
        private long primaryTokens;
        private long cacheReadTokens;
        private long cacheWriteTokens;

        void add(AnalyticsModels.ActivityRollupRow row) {
            users.add(row.userId());
            primaryTokens += row.tokensTotal();
            cacheReadTokens += row.tokensCacheRead();
            cacheWriteTokens += row.tokensCacheWrite();
        }

        AnalyticsModels.TokenDailyPoint toRow(LocalDate date) {
            return new AnalyticsModels.TokenDailyPoint(
                    date,
                    totalTokens(),
                    primaryTokens,
                    cacheReadTokens,
                    cacheWriteTokens,
                    users.size(),
                    ratio(totalTokens(), users.size()));
        }

        long totalTokens() {
            return primaryTokens + cacheReadTokens + cacheWriteTokens;
        }
    }

    private static final class TokenUserTotals {
        private final String userId;
        private final String username;
        private final String organization;
        private final String rdDepartment;
        private final String department;
        private final Set<LocalDate> dates = new java.util.HashSet<>();
        private long primaryTokens;
        private long cacheReadTokens;
        private long cacheWriteTokens;

        TokenUserTotals(AnalyticsModels.ActivityRollupRow row) {
            this.userId = row.userId();
            this.username = row.username();
            this.organization = row.organization();
            this.rdDepartment = row.rdDepartment();
            this.department = row.department();
        }

        void add(AnalyticsModels.ActivityRollupRow row, LocalDate date) {
            dates.add(date);
            primaryTokens += row.tokensTotal();
            cacheReadTokens += row.tokensCacheRead();
            cacheWriteTokens += row.tokensCacheWrite();
        }

        long primaryTokens() {
            return primaryTokens;
        }

        long totalTokens() {
            return primaryTokens + cacheReadTokens + cacheWriteTokens;
        }

        long cacheReadTokens() {
            return cacheReadTokens;
        }

        long cacheWriteTokens() {
            return cacheWriteTokens;
        }

        long tokenDays() {
            return dates.size();
        }

        double tokensPerTokenDay() {
            return dates.isEmpty() ? 0.0d : totalTokens() * 1.0d / dates.size();
        }

        AnalyticsModels.TokenUserRow toRow(double[] quartiles) {
            double average = tokensPerTokenDay();
            String band = average <= quartiles[0] ? "LIGHT"
                    : average <= quartiles[1] ? "EXPLORING"
                    : average <= quartiles[2] ? "REGULAR" : "POWER";
            return new AnalyticsModels.TokenUserRow(
                    userId,
                    username,
                    organization,
                    rdDepartment,
                    department,
                    totalTokens(),
                    primaryTokens,
                    cacheReadTokens,
                    cacheWriteTokens,
                    dates.size(),
                    average,
                    band);
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
            return userMessageCount >= 5 && activeDates.size() >= 2;
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
            return row.bucketStart().atZone(ANALYTICS_ZONE).toLocalDate();
        }

        private static String valueOr(String current, String candidate) {
            if (current == null || current.isBlank() || "未归属".equals(current)) {
                return candidate == null || candidate.isBlank() ? "未归属" : candidate;
            }
            return current;
        }
    }
}
