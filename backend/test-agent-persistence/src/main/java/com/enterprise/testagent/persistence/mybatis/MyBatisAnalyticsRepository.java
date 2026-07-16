package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackRating;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackReasonCode;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import com.enterprise.testagent.domain.analytics.AnalyticsRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 运营分析 MyBatis 仓储实现。看板查询只读 rollup 表，原始事实扫描仅供后台汇总任务调用。
 */
@Repository
public class MyBatisAnalyticsRepository implements AnalyticsRepository {

    private final AnalyticsMapper mapper;

    public MyBatisAnalyticsRepository(AnalyticsMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public List<AnalyticsModels.RawActivityRow> loadRawActivityFacts(Instant startInclusive, Instant endExclusive) {
        return mapper.loadRawActivityFacts(startInclusive, endExclusive).stream()
                .map(this::toRaw)
                .toList();
    }

    @Override
    public List<AnalyticsModels.DurationSample> loadRunDurationSamples(Instant startInclusive, Instant endExclusive) {
        return mapper.loadRunDurationSamples(startInclusive, endExclusive).stream()
                .map(row -> new AnalyticsModels.DurationSample(
                        row.bucketStart(),
                        row.userId(),
                        row.username(),
                        row.organization(),
                        row.rdDepartment(),
                        row.department(),
                        row.workspaceId(),
                        row.agentId(),
                        row.modelId(),
                        Math.max(0L, Duration.between(row.createdAt(), row.updatedAt()).toMillis())))
                .toList();
    }

    @Override
    public void deleteHourly(Instant startInclusive, Instant endExclusive) {
        mapper.deleteHourly(startInclusive, endExclusive);
    }

    @Override
    public void insertHourly(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt) {
        if (!rows.isEmpty()) {
            mapper.insertHourly(rows.stream().map(this::toActivityRow).toList(), updatedAt);
        }
    }

    @Override
    public List<AnalyticsModels.ActivityRollupRow> loadHourly(Instant startInclusive, Instant endExclusive) {
        return mapper.loadHourly(startInclusive, endExclusive).stream()
                .map(this::toRollup)
                .toList();
    }

    @Override
    public void deleteDaily(LocalDate startInclusive, LocalDate endInclusive) {
        mapper.deleteDaily(startInclusive, endInclusive);
    }

    @Override
    public void insertDaily(List<AnalyticsModels.ActivityRollupRow> rows, Instant updatedAt) {
        if (!rows.isEmpty()) {
            mapper.insertDaily(rows.stream().map(this::toActivityRow).toList(), updatedAt);
        }
    }

    @Override
    public void deleteDurationHistogram(Instant startInclusive, Instant endExclusive) {
        mapper.deleteDurationHistogram(startInclusive, endExclusive);
    }

    @Override
    public void insertDurationHistogram(List<AnalyticsModels.DurationHistogramRow> histogramRows, Instant updatedAt) {
        if (!histogramRows.isEmpty()) {
            mapper.insertDurationHistogram(histogramRows.stream().map(this::toHistogramRow).toList(), updatedAt);
        }
    }

    @Override
    public Optional<AnalyticsModels.Freshness> freshness(String jobName, Instant staleThreshold) {
        return Optional.ofNullable(mapper.freshness(jobName))
                .map(row -> {
                    AnalyticsModels.FreshnessStatus status = AnalyticsModels.FreshnessStatus.valueOf(row.status());
                    if (row.generatedAt() == null || row.generatedAt().isBefore(staleThreshold)) {
                        status = AnalyticsModels.FreshnessStatus.STALE;
                    }
                    return new AnalyticsModels.Freshness(row.generatedAt(), status, row.message());
                });
    }

    @Override
    public void updateWatermark(
            String jobName,
            Instant watermarkAt,
            AnalyticsModels.FreshnessStatus status,
            String message,
            String traceId,
            Instant updatedAt) {
        int affected = mapper.updateWatermark(
                jobName,
                watermarkAt,
                updatedAt,
                status.name(),
                truncate(message, 500),
                traceId,
                updatedAt);
        if (affected == 0) {
            mapper.insertWatermark(
                    jobName,
                    watermarkAt,
                    updatedAt,
                    status.name(),
                    truncate(message, 500),
                    traceId,
                    updatedAt);
        }
    }

    @Override
    public boolean tryAcquireLock(String lockName, String ownerId, Instant lockedUntil, Instant now) {
        if (mapper.updateLock(lockName, ownerId, lockedUntil, now) > 0) {
            return true;
        }
        try {
            return mapper.insertLock(lockName, ownerId, lockedUntil, now) > 0;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public void releaseLock(String lockName, String ownerId) {
        mapper.releaseLock(lockName, ownerId);
    }

    @Override
    public long countRegisteredUsers(AnalyticsModels.Filter filter) {
        return mapper.countRegisteredUsers(filter.organization(), filter.rdDepartment(), filter.department(), filter.userId());
    }

    @Override
    public long countEnabledUsers(AnalyticsModels.Filter filter) {
        return mapper.countEnabledUsers(filter.organization(), filter.rdDepartment(), filter.department(), filter.userId());
    }

    @Override
    public List<AnalyticsModels.ActivityRollupRow> queryRollups(AnalyticsModels.Filter filter) {
        if (filter.granularity() == AnalyticsModels.Granularity.HOUR) {
            return mapper.queryHourlyRollups(
                            filter.startTime(),
                            filter.endTime(),
                            filter.organization(),
                            filter.rdDepartment(),
                            filter.department(),
                            filter.userId(),
                            filter.agentId(),
                            filter.model(),
                            filter.workspaceId())
                    .stream()
                    .map(this::toRollup)
                    .toList();
        }
        LocalDate start = filter.startTime().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = filter.endTime().minusMillis(1).atZone(ZoneOffset.UTC).toLocalDate();
        return mapper.queryDailyRollups(
                        start,
                        end,
                        filter.organization(),
                        filter.rdDepartment(),
                        filter.department(),
                        filter.userId(),
                        filter.agentId(),
                        filter.model(),
                        filter.workspaceId())
                .stream()
                .map(this::toRollup)
                .toList();
    }

    @Override
    public long approximateP95DurationMs(AnalyticsModels.Filter filter) {
        List<AnalyticsDurationHistogramRow> rows = mapper.queryDurationHistogram(
                filter.startTime(),
                filter.endTime(),
                filter.organization(),
                filter.rdDepartment(),
                filter.department(),
                filter.agentId(),
                filter.model(),
                filter.workspaceId());
        long total = rows.stream().mapToLong(AnalyticsDurationHistogramRow::runCount).sum();
        if (total == 0) {
            return 0L;
        }
        long target = Math.max(1L, (long) Math.ceil(total * 0.95d));
        long cumulative = 0L;
        long lastFinite = 0L;
        for (AnalyticsDurationHistogramRow row : rows.stream()
                .sorted(Comparator.comparingLong(AnalyticsDurationHistogramRow::leMs))
                .toList()) {
            if (row.leMs() != Long.MAX_VALUE) {
                lastFinite = row.leMs();
            }
            cumulative += row.runCount();
            if (cumulative >= target) {
                return row.leMs() == Long.MAX_VALUE ? lastFinite : row.leMs();
            }
        }
        return lastFinite;
    }

    @Override
    public PageResponse<AnalyticsModels.FeedbackDetail> feedbackDetails(AnalyticsModels.Filter filter) {
        int pageSize = filter.pageSize();
        long offset = (long) Math.max(0, filter.page() - 1) * pageSize;
        List<AnalyticsModels.FeedbackDetail> items = mapper.feedbackDetails(
                        filter.startTime(),
                        filter.endTime(),
                        filter.organization(),
                        filter.rdDepartment(),
                        filter.department(),
                        filter.userId(),
                        filter.agentId(),
                        filter.model(),
                        filter.workspaceId(),
                        pageSize,
                        offset)
                .stream()
                .map(this::toFeedbackDetail)
                .toList();
        long total = mapper.countFeedbackDetails(
                filter.startTime(),
                filter.endTime(),
                filter.organization(),
                filter.rdDepartment(),
                filter.department(),
                filter.userId(),
                filter.agentId(),
                filter.model(),
                filter.workspaceId());
        return new PageResponse<>(items, filter.page(), pageSize, total);
    }

    @Override
    public Map<String, Long> negativeReasonCounts(AnalyticsModels.Filter filter) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : mapper.negativeReasonCounts(
                filter.startTime(),
                filter.endTime(),
                filter.organization(),
                filter.rdDepartment(),
                filter.department(),
                filter.userId(),
                filter.agentId(),
                filter.model(),
                filter.workspaceId(),
                filter.topN())) {
            Object reason = row.get("REASON_CODE");
            if (reason == null) {
                reason = row.get("reason_code");
            }
            Object count = row.get("REASON_COUNT");
            if (count == null) {
                count = row.get("reason_count");
            }
            if (reason != null && count instanceof Number number) {
                counts.put(reason.toString(), number.longValue());
            }
        }
        return counts;
    }

    @Override
    public PageResponse<AnalyticsModels.ExceptionDetail> exceptionDetails(AnalyticsModels.Filter filter) {
        int pageSize = filter.pageSize();
        long offset = (long) Math.max(0, filter.page() - 1) * pageSize;
        List<AnalyticsModels.ExceptionDetail> items = mapper.exceptionDetails(
                        filter.startTime(),
                        filter.endTime(),
                        filter.organization(),
                        filter.rdDepartment(),
                        filter.department(),
                        filter.userId(),
                        filter.agentId(),
                        filter.model(),
                        filter.workspaceId(),
                        pageSize,
                        offset)
                .stream()
                .map(this::toExceptionDetail)
                .toList();
        long total = mapper.countExceptionDetails(
                filter.startTime(),
                filter.endTime(),
                filter.organization(),
                filter.rdDepartment(),
                filter.department(),
                filter.userId(),
                filter.agentId(),
                filter.model(),
                filter.workspaceId());
        return new PageResponse<>(items, filter.page(), pageSize, total);
    }

    @Override
    public List<AnalyticsModels.OrganizationUsageRow> organizationRows(AnalyticsModels.Filter filter, String dimension) {
        Map<String, MutableOrg> grouped = new LinkedHashMap<>();
        Map<String, AnalyticsOrganizationUserCountRow> userCounts = new LinkedHashMap<>();
        for (AnalyticsOrganizationUserCountRow row : mapper.organizationUserCounts(
                dimension,
                filter.organization(),
                filter.rdDepartment(),
                filter.department(),
                filter.userId())) {
            userCounts.put(row.name(), row);
        }
        for (AnalyticsModels.ActivityRollupRow row : queryRollups(filter)) {
            String key = switch (dimension) {
                case "rdDepartment" -> row.rdDepartment();
                case "department" -> row.department();
                default -> row.organization();
            };
            grouped.computeIfAbsent(key == null || key.isBlank() ? "未归属" : key, ignored -> new MutableOrg())
                    .add(row);
        }
        List<AnalyticsModels.OrganizationUsageRow> rows = new ArrayList<>();
        userCounts.forEach((key, value) -> grouped.computeIfAbsent(key, ignored -> new MutableOrg())
                .withUserCounts(value.registeredUsers(), value.enabledUsers()));
        grouped.forEach((key, value) -> rows.add(value.toRow(dimension, key)));
        return rows;
    }

    private AnalyticsModels.RawActivityRow toRaw(AnalyticsActivityRow row) {
        return new AnalyticsModels.RawActivityRow(
                row.bucketStart(),
                row.userId(),
                row.username(),
                row.organization(),
                row.rdDepartment(),
                row.department(),
                row.workspaceId(),
                row.agentId(),
                row.modelId(),
                row.loginCount(),
                row.sessionCount(),
                row.activeSessionCount(),
                row.emptySessionCount(),
                row.continuousSessionCount(),
                row.userMessageCount(),
                row.assistantMessageCount(),
                row.runCount(),
                row.succeededRunCount(),
                row.failedRunCount(),
                row.cancelledRunCount(),
                row.activeTerminationCount(),
                row.validInteractionCount(),
                row.positiveFeedbackCount(),
                row.negativeFeedbackCount(),
                row.diffProposedCount(),
                row.diffAcceptedCount(),
                row.diffRejectedCount(),
                row.tokensInput(),
                row.tokensOutput(),
                row.tokensReasoning(),
                row.durationTotalMs());
    }

    private AnalyticsModels.ActivityRollupRow toRollup(AnalyticsActivityRow row) {
        return new AnalyticsModels.ActivityRollupRow(
                row.bucketStart(),
                row.activityDate(),
                row.userId(),
                row.username(),
                row.organization(),
                row.rdDepartment(),
                row.department(),
                row.workspaceId(),
                row.agentId(),
                row.modelId(),
                row.loginCount(),
                row.sessionCount(),
                row.activeSessionCount(),
                row.emptySessionCount(),
                row.continuousSessionCount(),
                row.userMessageCount(),
                row.assistantMessageCount(),
                row.runCount(),
                row.succeededRunCount(),
                row.failedRunCount(),
                row.cancelledRunCount(),
                row.activeTerminationCount(),
                row.validInteractionCount(),
                row.positiveFeedbackCount(),
                row.negativeFeedbackCount(),
                row.diffProposedCount(),
                row.diffAcceptedCount(),
                row.diffRejectedCount(),
                row.tokensInput(),
                row.tokensOutput(),
                row.tokensReasoning(),
                row.tokensTotal(),
                row.durationTotalMs(),
                row.durationRunCount(),
                row.firstActivityAt(),
                row.lastActivityAt());
    }

    private AnalyticsActivityRow toActivityRow(AnalyticsModels.ActivityRollupRow row) {
        return new AnalyticsActivityRow(
                row.bucketStart(),
                row.activityDate(),
                row.userId(),
                row.username(),
                row.organization(),
                row.rdDepartment(),
                row.department(),
                row.workspaceId(),
                row.agentId(),
                row.modelId(),
                row.loginCount(),
                row.sessionCount(),
                row.activeSessionCount(),
                row.emptySessionCount(),
                row.continuousSessionCount(),
                row.userMessageCount(),
                row.assistantMessageCount(),
                row.runCount(),
                row.succeededRunCount(),
                row.failedRunCount(),
                row.cancelledRunCount(),
                row.activeTerminationCount(),
                row.validInteractionCount(),
                row.positiveFeedbackCount(),
                row.negativeFeedbackCount(),
                row.diffProposedCount(),
                row.diffAcceptedCount(),
                row.diffRejectedCount(),
                row.tokensInput(),
                row.tokensOutput(),
                row.tokensReasoning(),
                row.tokensTotal(),
                row.durationTotalMs(),
                row.durationRunCount(),
                row.firstActivityAt(),
                row.lastActivityAt());
    }

    private AnalyticsDurationHistogramRow toHistogramRow(AnalyticsModels.DurationHistogramRow row) {
        return new AnalyticsDurationHistogramRow(
                row.bucketStart(),
                row.organization(),
                row.rdDepartment(),
                row.department(),
                row.workspaceId(),
                row.agentId(),
                row.modelId(),
                row.leMs(),
                row.runCount());
    }

    private AnalyticsModels.FeedbackDetail toFeedbackDetail(AnalyticsFeedbackDetailRow row) {
        return new AnalyticsModels.FeedbackDetail(
                row.feedbackId(),
                row.userId(),
                row.username(),
                row.organization(),
                row.rdDepartment(),
                row.department(),
                row.sessionId(),
                row.runId(),
                row.messageId(),
                AiMessageFeedbackRating.valueOf(row.rating()),
                row.reasonCode() == null ? null : AiMessageFeedbackReasonCode.valueOf(row.reasonCode()),
                row.comment(),
                row.createdAt(),
                row.updatedAt());
    }

    private AnalyticsModels.ExceptionDetail toExceptionDetail(AnalyticsExceptionDetailRow row) {
        return new AnalyticsModels.ExceptionDetail(
                row.runId(),
                row.userId(),
                row.username(),
                row.organization(),
                row.rdDepartment(),
                row.department(),
                row.workspaceId(),
                row.agentId(),
                row.modelId(),
                row.status(),
                row.createdAt(),
                row.updatedAt());
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : numerator * 1.0d / denominator;
    }

    private static final class MutableOrg {
        private final java.util.Set<String> loginUsers = new java.util.HashSet<>();
        private final java.util.Set<String> activeUsers = new java.util.HashSet<>();
        private final java.util.Set<String> deepUsers = new java.util.HashSet<>();
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
            if (row.loginCount() > 0) {
                loginUsers.add(row.userId());
            }
            if (row.userMessageCount() > 0 || row.runCount() > 0 || row.positiveFeedbackCount() + row.negativeFeedbackCount() > 0
                    || row.diffAcceptedCount() + row.diffRejectedCount() > 0) {
                activeUsers.add(row.userId());
            }
            if (row.runCount() >= 3 || row.userMessageCount() >= 5 || row.diffAcceptedCount() > 0
                    || row.positiveFeedbackCount() + row.negativeFeedbackCount() > 0) {
                deepUsers.add(row.userId());
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

        MutableOrg withUserCounts(long registeredUsers, long enabledUsers) {
            this.registeredUsers = registeredUsers;
            this.enabledUsers = enabledUsers;
            return this;
        }

        AnalyticsModels.OrganizationUsageRow toRow(String dimension, String name) {
            return new AnalyticsModels.OrganizationUsageRow(
                    dimension,
                    name,
                    registeredUsers,
                    enabledUsers,
                    loginUsers.size(),
                    activeUsers.size(),
                    deepUsers.size(),
                    ratio(activeUsers.size(), enabledUsers),
                    ratio(deepUsers.size(), activeUsers.size()),
                    runCount,
                    succeededRuns,
                    failedRuns,
                    cancelledRuns,
                    positiveFeedback,
                    negativeFeedback,
                    diffAccepted,
                    diffRejected,
                    totalTokens,
                    ratio(succeededRuns, runCount),
                    ratio(positiveFeedback, positiveFeedback + negativeFeedback),
                    ratio(diffAccepted, diffProposed));
        }
    }
}
