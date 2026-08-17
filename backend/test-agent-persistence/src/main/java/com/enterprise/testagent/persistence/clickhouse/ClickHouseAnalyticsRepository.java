package com.enterprise.testagent.persistence.clickhouse;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackRating;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackReasonCode;
import com.enterprise.testagent.domain.analytics.AnalyticsEventOutboxRepository;
import com.enterprise.testagent.domain.analytics.AnalyticsEventSink;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.analytics.AnalyticsRepository;
import com.enterprise.testagent.persistence.mybatis.AnalyticsActivityRow;
import com.enterprise.testagent.persistence.mybatis.AnalyticsEventOutboxMapper;
import com.enterprise.testagent.persistence.mybatis.AnalyticsExceptionDetailRow;
import com.enterprise.testagent.persistence.mybatis.AnalyticsFeedbackDetailRow;
import com.enterprise.testagent.persistence.mybatis.AnalyticsMapper;
import com.enterprise.testagent.persistence.mybatis.AnalyticsOrganizationUserCountRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** ClickHouse 运营仓储；PostgreSQL mapper 只提供跨节点任务锁，不参与运营查询。 */
@Repository
@ConditionalOnProperty(name = "test-agent.analytics.clickhouse.enabled", havingValue = "true")
public class ClickHouseAnalyticsRepository implements AnalyticsRepository, AnalyticsEventSink {

    private static final ZoneId ANALYTICS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ClickHouseAnalyticsMapper mapper;
    private final AnalyticsMapper masterDataMapper;

    public ClickHouseAnalyticsRepository(
            ClickHouseAnalyticsMapper mapper,
            AnalyticsMapper masterDataMapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.masterDataMapper = Objects.requireNonNull(masterDataMapper, "masterDataMapper must not be null");
    }

    @Override
    public void append(List<AnalyticsEventOutboxRepository.Event> events, Instant ingestedAt) {
        if (!events.isEmpty()) {
            mapper.insertEvents(events, ingestedAt);
        }
    }

    @Override
    public long countEventsByPrefix(String eventIdPrefix) {
        return available(() -> mapper.countEventsByPrefix(eventIdPrefix));
    }

    @Override
    public long countActivityFactsByPrefix(String eventIdPrefix) {
        return available(() -> mapper.countActivityFactsByPrefix(eventIdPrefix));
    }

    @Override
    public long countUserDimensionFactsByPrefix(String eventIdPrefix) {
        return available(() -> mapper.countUserDimensionFactsByPrefix(eventIdPrefix));
    }

    @Override
    public List<AnalyticsModels.RawActivityRow> loadRawActivityFacts(Instant startInclusive, Instant endExclusive) {
        return available(() -> mapper.loadRawActivityFacts(startInclusive, endExclusive).stream()
                .map(this::toRaw)
                .toList());
    }

    @Override
    public List<AnalyticsModels.DurationSample> loadRunDurationSamples(Instant startInclusive, Instant endExclusive) {
        return loadRawActivityFacts(startInclusive, endExclusive).stream()
                .filter(row -> row.durationMs() > 0)
                .map(row -> new AnalyticsModels.DurationSample(
                        row.occurredAt(), row.userId(), row.username(), row.organization(),
                        row.rdDepartment(), row.department(), row.workspaceId(), row.agentId(),
                        row.modelId(), row.durationMs()))
                .toList();
    }

    /** ReplacingMergeTree 通过新版本替换旧汇总，不执行昂贵的同步 mutation。 */
    @Override
    public void deleteHourly(Instant startInclusive, Instant endExclusive) {
    }

    @Override
    public void insertHourly(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt) {
        if (!rows.isEmpty()) {
            available(() -> mapper.insertHourly(rows.stream().map(this::toActivityRow).toList(), updatedAt));
        }
    }

    @Override
    public List<AnalyticsModels.ActivityRollupRow> loadHourly(Instant startInclusive, Instant endExclusive) {
        return available(() -> mapper.loadHourly(startInclusive, endExclusive).stream()
                .map(this::toRollup)
                .toList());
    }

    @Override
    public void deleteDaily(LocalDate startInclusive, LocalDate endInclusive) {
    }

    @Override
    public void insertDaily(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt) {
        if (!rows.isEmpty()) {
            available(() -> mapper.insertDaily(rows.stream().map(this::toActivityRow).toList(), updatedAt));
        }
    }

    @Override
    public void deleteDurationHistogram(Instant startInclusive, Instant endExclusive) {
    }

    @Override
    public void insertDurationHistogram(List<AnalyticsModels.DurationHistogramRow> histogramRows, Instant updatedAt) {
        // ClickHouse 直接以 quantileExact 查询 p95；表仅为历史兼容保留。
    }

    @Override
    public Optional<AnalyticsModels.Freshness> freshness(String jobName, Instant staleThreshold) {
        return available(() -> Optional.ofNullable(mapper.freshness(jobName)).map(row -> {
            AnalyticsModels.FreshnessStatus status = AnalyticsModels.FreshnessStatus.valueOf(row.status());
            if (row.generatedAt() == null || row.generatedAt().isBefore(staleThreshold)) {
                status = AnalyticsModels.FreshnessStatus.STALE;
            }
            return new AnalyticsModels.Freshness(
                    row.generatedAt(), status, row.message(), row.coverageStart(),
                    row.coverageEnd(), row.attributionMode());
        }));
    }

    @Override
    public void updateWatermark(
            String jobName,
            Instant watermarkAt,
            AnalyticsModels.FreshnessStatus status,
            String message,
            String traceId,
            Instant updatedAt) {
        available(() -> mapper.insertWatermark(
                jobName, watermarkAt, updatedAt, status.name(), truncate(message),
                watermarkAt.minusSeconds(7 * 86_400L), watermarkAt,
                "EVENT_SNAPSHOT", updatedAt.toEpochMilli()));
    }

    @Override
    public boolean tryAcquireLock(String lockName, String ownerId, Instant lockedUntil, Instant now) {
        if (masterDataMapper.updateLock(lockName, ownerId, lockedUntil, now) > 0) {
            return true;
        }
        try {
            return masterDataMapper.insertLock(lockName, ownerId, lockedUntil, now) > 0;
        } catch (RuntimeException duplicateLock) {
            return false;
        }
    }

    @Override
    public void releaseLock(String lockName, String ownerId) {
        masterDataMapper.releaseLock(lockName, ownerId);
    }

    @Override
    public long countRegisteredUsers(AnalyticsModels.Filter filter) {
        return available(() -> mapper.countRegisteredUsers(filter));
    }

    @Override
    public long countEnabledUsers(AnalyticsModels.Filter filter) {
        return available(() -> mapper.countEnabledUsers(filter));
    }

    @Override
    public List<AnalyticsModels.ActivityRollupRow> queryRollups(AnalyticsModels.Filter filter) {
        return available(() -> {
            List<AnalyticsActivityRow> rows;
            if (filter.granularity() == AnalyticsModels.Granularity.HOUR) {
                rows = mapper.queryHourlyRollups(filter);
            } else {
                LocalDate start = filter.startTime().atZone(ANALYTICS_ZONE).toLocalDate();
                LocalDate end = filter.endTime().minusNanos(1).atZone(ANALYTICS_ZONE).toLocalDate();
                rows = mapper.queryDailyRollups(filter, start, end);
            }
            return rows.stream().map(this::toRollup).toList();
        });
    }

    @Override
    public long approximateP95DurationMs(AnalyticsModels.Filter filter) {
        return available(() -> Optional.ofNullable(mapper.p95DurationMs(filter)).orElse(0L));
    }

    @Override
    public PageResponse<AnalyticsModels.FeedbackDetail> feedbackDetails(AnalyticsModels.Filter filter) {
        return available(() -> {
            int pageSize = filter.pageSize();
            long offset = (long) Math.max(0, filter.page() - 1) * pageSize;
            List<AnalyticsModels.FeedbackDetail> items = mapper.feedbackDetails(filter, pageSize, offset).stream()
                    .map(this::toFeedbackDetail)
                    .toList();
            return new PageResponse<>(items, filter.page(), pageSize, mapper.countFeedbackDetails(filter));
        });
    }

    @Override
    public Map<String, Long> negativeReasonCounts(AnalyticsModels.Filter filter) {
        return available(() -> {
            Map<String, Long> result = new LinkedHashMap<>();
            for (Map<String, Object> row : mapper.negativeReasonCounts(filter, filter.topN())) {
                Object reason = value(row, "reason_code", "REASON_CODE");
                Object count = value(row, "reason_count", "REASON_COUNT");
                if (reason != null && count instanceof Number number) {
                    result.put(reason.toString(), number.longValue());
                }
            }
            return result;
        });
    }

    @Override
    public PageResponse<AnalyticsModels.ExceptionDetail> exceptionDetails(AnalyticsModels.Filter filter) {
        return available(() -> {
            int pageSize = filter.pageSize();
            long offset = (long) Math.max(0, filter.page() - 1) * pageSize;
            List<AnalyticsModels.ExceptionDetail> items = mapper.exceptionDetails(filter, pageSize, offset).stream()
                    .map(this::toExceptionDetail)
                    .toList();
            return new PageResponse<>(items, filter.page(), pageSize, mapper.countExceptionDetails(filter));
        });
    }

    @Override
    public List<AnalyticsModels.OrganizationUsageRow> organizationRows(
            AnalyticsModels.Filter filter,
            String dimension) {
        Map<String, MutableOrg> grouped = new LinkedHashMap<>();
        for (AnalyticsModels.ActivityRollupRow row : queryRollups(filter)) {
            String raw = switch (dimension) {
                case "rdDepartment" -> row.rdDepartment();
                case "department" -> row.department();
                default -> row.organization();
            };
            grouped.computeIfAbsent(blank(raw) ? "未归属" : raw, ignored -> new MutableOrg()).add(row);
        }
        for (AnalyticsOrganizationUserCountRow row : available(() -> mapper.organizationUserCounts(dimension, filter))) {
            grouped.computeIfAbsent(row.name(), ignored -> new MutableOrg())
                    .withUserCounts(row.registeredUsers(), row.enabledUsers());
        }
        List<AnalyticsModels.OrganizationUsageRow> rows = new ArrayList<>();
        grouped.forEach((name, value) -> rows.add(value.toRow(dimension, name)));
        return rows;
    }

    @Override
    public List<AnalyticsModels.CapabilityUsageRow> capabilityUsage(AnalyticsModels.Filter filter) {
        return available(() -> mapper.capabilityUsage(filter));
    }

    @Override
    public List<AnalyticsModels.FilterOption> organizations() {
        return available(mapper::organizations);
    }

    @Override
    public List<AnalyticsModels.FilterOption> rdDepartments(String organization) {
        return available(() -> mapper.rdDepartments(organization));
    }

    @Override
    public List<AnalyticsModels.FilterOption> departments(String organization, String rdDepartment) {
        return available(() -> mapper.departments(organization, rdDepartment));
    }

    private AnalyticsModels.RawActivityRow toRaw(AnalyticsActivityRow row) {
        return new AnalyticsModels.RawActivityRow(
                row.bucketStart(), row.userId(), row.username(), row.organization(), row.rdDepartment(),
                row.department(), row.workspaceId(), row.agentId(), row.modelId(), row.loginCount(),
                row.sessionCount(), row.activeSessionCount(), row.emptySessionCount(), row.continuousSessionCount(),
                row.userMessageCount(), row.assistantMessageCount(), row.runCount(), row.succeededRunCount(),
                row.failedRunCount(), row.cancelledRunCount(), row.activeTerminationCount(),
                row.validInteractionCount(), row.positiveFeedbackCount(), row.negativeFeedbackCount(),
                row.diffProposedCount(), row.diffAcceptedCount(), row.diffRejectedCount(), row.tokensInput(),
                row.tokensOutput(), row.tokensReasoning(), row.tokensCacheRead(), row.tokensCacheWrite(),
                row.durationTotalMs());
    }

    private AnalyticsModels.ActivityRollupRow toRollup(AnalyticsActivityRow row) {
        return new AnalyticsModels.ActivityRollupRow(
                row.bucketStart(), row.activityDate(), row.userId(), row.username(), row.organization(),
                row.rdDepartment(), row.department(), row.workspaceId(), row.agentId(), row.modelId(),
                row.loginCount(), row.sessionCount(), row.activeSessionCount(), row.emptySessionCount(),
                row.continuousSessionCount(), row.userMessageCount(), row.assistantMessageCount(), row.runCount(),
                row.succeededRunCount(), row.failedRunCount(), row.cancelledRunCount(), row.activeTerminationCount(),
                row.validInteractionCount(), row.positiveFeedbackCount(), row.negativeFeedbackCount(),
                row.diffProposedCount(), row.diffAcceptedCount(), row.diffRejectedCount(), row.tokensInput(),
                row.tokensOutput(), row.tokensReasoning(), row.tokensCacheRead(), row.tokensCacheWrite(),
                row.tokensTotal(), row.durationTotalMs(), row.durationRunCount(), row.firstActivityAt(),
                row.lastActivityAt());
    }

    private AnalyticsActivityRow toActivityRow(AnalyticsModels.ActivityRollupRow row) {
        // ClickHouse 日表复用小时表结构，日汇总用业务时区零点填充非空 bucket_start。
        Instant bucketStart = row.bucketStart() == null
                ? row.activityDate().atStartOfDay(ANALYTICS_ZONE).toInstant()
                : row.bucketStart();
        return new AnalyticsActivityRow(
                bucketStart, row.activityDate(), row.userId(), row.username(), row.organization(),
                row.rdDepartment(), row.department(), value(row.workspaceId()), value(row.agentId()), value(row.modelId()),
                row.loginCount(), row.sessionCount(), row.activeSessionCount(), row.emptySessionCount(),
                row.continuousSessionCount(), row.userMessageCount(), row.assistantMessageCount(), row.runCount(),
                row.succeededRunCount(), row.failedRunCount(), row.cancelledRunCount(), row.activeTerminationCount(),
                row.validInteractionCount(), row.positiveFeedbackCount(), row.negativeFeedbackCount(),
                row.diffProposedCount(), row.diffAcceptedCount(), row.diffRejectedCount(), row.tokensInput(),
                row.tokensOutput(), row.tokensReasoning(), row.tokensCacheRead(), row.tokensCacheWrite(),
                row.tokensTotal(), row.durationTotalMs(), row.durationRunCount(), row.firstActivityAt(),
                row.lastActivityAt());
    }

    private AnalyticsModels.FeedbackDetail toFeedbackDetail(AnalyticsFeedbackDetailRow row) {
        return new AnalyticsModels.FeedbackDetail(
                row.feedbackId(), row.userId(), row.username(), row.organization(), row.rdDepartment(),
                row.department(), row.sessionId(), row.runId(), row.messageId(),
                AiMessageFeedbackRating.valueOf(row.rating()),
                blank(row.reasonCode()) ? null : AiMessageFeedbackReasonCode.valueOf(row.reasonCode()),
                null, row.createdAt(), row.updatedAt());
    }

    private AnalyticsModels.ExceptionDetail toExceptionDetail(AnalyticsExceptionDetailRow row) {
        return new AnalyticsModels.ExceptionDetail(
                row.runId(), row.userId(), row.username(), row.organization(), row.rdDepartment(),
                row.department(), row.workspaceId(), row.agentId(), row.modelId(), row.status(),
                row.createdAt(), row.updatedAt());
    }

    private <T> T available(Supplier<T> action) {
        try {
            return action.get();
        } catch (PlatformException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PlatformException(
                    ErrorCode.ANALYTICS_UNAVAILABLE,
                    "ClickHouse 运营分析暂不可用",
                    Map.of(),
                    exception);
        }
    }

    private void available(Runnable action) {
        available(() -> {
            action.run();
            return null;
        });
    }

    private static Object value(Map<String, Object> row, String lower, String upper) {
        return row.containsKey(lower) ? row.get(lower) : row.get(upper);
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : numerator * 1.0d / denominator;
    }

    private static final class MutableOrg {
        private final Set<String> loginUsers = new HashSet<>();
        private final Set<String> activeUsers = new HashSet<>();
        private final Map<String, Set<LocalDate>> activeDates = new LinkedHashMap<>();
        private final Map<String, Long> messages = new LinkedHashMap<>();
        private long registeredUsers;
        private long enabledUsers;
        private long runCount;
        private long succeededRuns;
        private long failedRuns;
        private long cancelledRuns;
        private long positiveFeedback;
        private long negativeFeedback;
        private long diffAccepted;
        private long diffRejected;
        private long diffProposed;
        private long totalTokens;

        void add(AnalyticsModels.ActivityRollupRow row) {
            if (row.loginCount() > 0) loginUsers.add(row.userId());
            if (row.userMessageCount() > 0) {
                activeUsers.add(row.userId());
                LocalDate activityDate = row.activityDate() == null
                        ? row.bucketStart().atZone(ANALYTICS_ZONE).toLocalDate()
                        : row.activityDate();
                activeDates.computeIfAbsent(row.userId(), ignored -> new HashSet<>()).add(activityDate);
                messages.merge(row.userId(), row.userMessageCount(), Long::sum);
            }
            runCount += row.runCount();
            succeededRuns += row.succeededRunCount();
            failedRuns += row.failedRunCount();
            cancelledRuns += row.cancelledRunCount();
            positiveFeedback += row.positiveFeedbackCount();
            negativeFeedback += row.negativeFeedbackCount();
            diffAccepted += row.diffAcceptedCount();
            diffRejected += row.diffRejectedCount();
            diffProposed += row.diffProposedCount();
            totalTokens += row.tokensTotal();
        }

        MutableOrg withUserCounts(long registered, long enabled) {
            registeredUsers = registered;
            enabledUsers = enabled;
            return this;
        }

        AnalyticsModels.OrganizationUsageRow toRow(String dimension, String name) {
            long deep = activeUsers.stream()
                    .filter(user -> activeDates.getOrDefault(user, Set.of()).size() >= 2)
                    .filter(user -> messages.getOrDefault(user, 0L) >= 5)
                    .count();
            return new AnalyticsModels.OrganizationUsageRow(
                    dimension, name, registeredUsers, enabledUsers, loginUsers.size(), activeUsers.size(), deep,
                    ratio(activeUsers.size(), enabledUsers), ratio(deep, activeUsers.size()), runCount,
                    succeededRuns, failedRuns, cancelledRuns, positiveFeedback, negativeFeedback,
                    diffAccepted, diffRejected, totalTokens, ratio(succeededRuns, runCount),
                    ratio(positiveFeedback, positiveFeedback + negativeFeedback), ratio(diffAccepted, diffProposed));
        }
    }
}
