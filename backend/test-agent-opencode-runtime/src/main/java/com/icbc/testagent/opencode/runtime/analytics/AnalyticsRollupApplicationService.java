package com.icbc.testagent.opencode.runtime.analytics;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.analytics.AnalyticsModels;
import com.icbc.testagent.domain.analytics.AnalyticsRepository;
import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 运营分析异步汇总服务：按有限窗口重建小时/日汇总，避免主链路同步统计写放大。
 */
@Service
public class AnalyticsRollupApplicationService {

    public static final String JOB_NAME = "analytics-rollup";
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsRollupApplicationService.class);
    private static final long[] DURATION_BUCKETS = {
            1_000L, 3_000L, 5_000L, 10_000L, 30_000L, 60_000L, 120_000L, 300_000L, Long.MAX_VALUE
    };

    private final AnalyticsRepository repository;
    private final String ownerId;

    public AnalyticsRollupApplicationService(AnalyticsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.ownerId = ownerId();
    }

    /**
     * 默认重算最近 2 小时 hourly，并从 hourly 修正最近 7 天 daily。
     */
    public void rollupRecent(String traceId) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        Instant windowEnd = now.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        if (!repository.tryAcquireLock(JOB_NAME, ownerId, now.plusSeconds(240), now)) {
            LOGGER.debug("Analytics rollup skipped because another owner holds the DB lock, ownerId={}, traceId={}", ownerId, traceId);
            return;
        }
        try {
            rebuildHourly(windowStart, windowEnd, traceId);
            LocalDate dailyStart = now.minus(7, ChronoUnit.DAYS).atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate dailyEnd = now.atZone(ZoneOffset.UTC).toLocalDate();
            rebuildDailyFromHourly(dailyStart, dailyEnd, traceId);
            repository.updateWatermark(
                    JOB_NAME,
                    windowEnd,
                    AnalyticsModels.FreshnessStatus.FRESH,
                    "统计已更新",
                    traceId,
                    Instant.now());
        } catch (RuntimeException exception) {
            repository.updateWatermark(
                    JOB_NAME,
                    windowEnd,
                    AnalyticsModels.FreshnessStatus.FAILED,
                    exception.getMessage(),
                    traceId,
                    Instant.now());
            throw exception;
        } finally {
            repository.releaseLock(JOB_NAME, ownerId);
        }
    }

    /**
     * 按小时 bucket 删除重建，保证失败重试幂等。
     */
    public void rebuildHourly(Instant startInclusive, Instant endExclusive, String traceId) {
        if (!startInclusive.isBefore(endExclusive)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "汇总时间窗口非法");
        }
        Instant start = startInclusive.truncatedTo(ChronoUnit.HOURS);
        Instant end = endExclusive.truncatedTo(ChronoUnit.HOURS);
        Map<ActivityKey, ActivityAccumulator> grouped = new LinkedHashMap<>();
        for (AnalyticsModels.RawActivityRow row : repository.loadRawActivityFacts(start, end)) {
            Instant bucket = row.occurredAt().truncatedTo(ChronoUnit.HOURS);
            grouped.computeIfAbsent(ActivityKey.from(bucket, row), ignored -> ActivityAccumulator.from(row))
                    .add(row);
        }
        List<AnalyticsModels.DurationSample> durationSamples = repository.loadRunDurationSamples(start, end);
        for (AnalyticsModels.DurationSample sample : durationSamples) {
            Instant bucket = sample.bucketStart().truncatedTo(ChronoUnit.HOURS);
            ActivityKey key = ActivityKey.from(bucket, sample);
            grouped.computeIfAbsent(key, ignored -> ActivityAccumulator.from(sample))
                    .addDuration(sample.durationMs(), sample.bucketStart());
        }
        List<AnalyticsModels.ActivityRollupRow> rows = grouped.values().stream()
                .map(ActivityAccumulator::toHourlyRow)
                .toList();
        repository.deleteHourly(start, end);
        repository.insertHourly(rows, Instant.now());
        repository.deleteDurationHistogram(start, end);
        repository.insertDurationHistogram(durationHistogram(durationSamples), Instant.now());
        LOGGER.info("Analytics hourly rollup rebuilt, start={}, end={}, rows={}, traceId={}", start, end, rows.size(), traceId);
    }

    /**
     * 由小时表生成日表，避免每日查询重新扫描原始大表。
     */
    public void rebuildDailyFromHourly(LocalDate startInclusive, LocalDate endInclusive, String traceId) {
        Instant start = startInclusive.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endInclusive.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<ActivityKey, ActivityAccumulator> grouped = new LinkedHashMap<>();
        for (AnalyticsModels.ActivityRollupRow row : repository.loadHourly(start, end)) {
            LocalDate date = row.bucketStart().atZone(ZoneOffset.UTC).toLocalDate();
            ActivityKey key = ActivityKey.from(date, row);
            grouped.computeIfAbsent(key, ignored -> ActivityAccumulator.from(date, row))
                    .add(row);
        }
        List<AnalyticsModels.ActivityRollupRow> rows = grouped.values().stream()
                .map(ActivityAccumulator::toDailyRow)
                .toList();
        repository.deleteDaily(startInclusive, endInclusive);
        repository.insertDaily(rows, Instant.now());
        LOGGER.info("Analytics daily rollup rebuilt, start={}, end={}, rows={}, traceId={}", startInclusive, endInclusive, rows.size(), traceId);
    }

    private List<AnalyticsModels.DurationHistogramRow> durationHistogram(List<AnalyticsModels.DurationSample> samples) {
        Map<HistogramKey, Long> counts = new LinkedHashMap<>();
        for (AnalyticsModels.DurationSample sample : samples) {
            long leMs = bucketLe(sample.durationMs());
            HistogramKey key = new HistogramKey(
                    sample.bucketStart().truncatedTo(ChronoUnit.HOURS),
                    sample.organization(),
                    sample.rdDepartment(),
                    sample.department(),
                    sample.workspaceId(),
                    sample.agentId(),
                    sample.modelId(),
                    leMs);
            counts.merge(key, 1L, Long::sum);
        }
        List<AnalyticsModels.DurationHistogramRow> rows = new ArrayList<>();
        counts.forEach((key, count) -> rows.add(new AnalyticsModels.DurationHistogramRow(
                key.bucketStart(),
                key.organization(),
                key.rdDepartment(),
                key.department(),
                key.workspaceId(),
                key.agentId(),
                key.modelId(),
                key.leMs(),
                count)));
        return rows;
    }

    private long bucketLe(long durationMs) {
        for (long bucket : DURATION_BUCKETS) {
            if (durationMs <= bucket) {
                return bucket;
            }
        }
        return Long.MAX_VALUE;
    }

    private static String ownerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
        } catch (Exception exception) {
            return "analytics-rollup-" + ProcessHandle.current().pid();
        }
    }

    private record ActivityKey(
            Instant bucketStart,
            LocalDate activityDate,
            String userId,
            String workspaceId,
            String agentId,
            String modelId) {
        static ActivityKey from(Instant bucketStart, AnalyticsModels.RawActivityRow row) {
            return new ActivityKey(bucketStart, null, row.userId(), row.workspaceId(), row.agentId(), row.modelId());
        }

        static ActivityKey from(Instant bucketStart, AnalyticsModels.DurationSample row) {
            return new ActivityKey(bucketStart, null, row.userId(), row.workspaceId(), row.agentId(), row.modelId());
        }

        static ActivityKey from(LocalDate activityDate, AnalyticsModels.ActivityRollupRow row) {
            return new ActivityKey(null, activityDate, row.userId(), row.workspaceId(), row.agentId(), row.modelId());
        }
    }

    private record HistogramKey(
            Instant bucketStart,
            String organization,
            String rdDepartment,
            String department,
            String workspaceId,
            String agentId,
            String modelId,
            long leMs) {
    }

    private static final class ActivityAccumulator {
        private Instant bucketStart;
        private LocalDate activityDate;
        private final String userId;
        private String username;
        private String organization;
        private String rdDepartment;
        private String department;
        private final String workspaceId;
        private final String agentId;
        private final String modelId;
        private long loginCount;
        private long sessionCount;
        private long activeSessionCount;
        private long emptySessionCount;
        private long continuousSessionCount;
        private long userMessageCount;
        private long assistantMessageCount;
        private long runCount;
        private long succeededRunCount;
        private long failedRunCount;
        private long cancelledRunCount;
        private long activeTerminationCount;
        private long validInteractionCount;
        private long positiveFeedbackCount;
        private long negativeFeedbackCount;
        private long diffProposedCount;
        private long diffAcceptedCount;
        private long diffRejectedCount;
        private long tokensInput;
        private long tokensOutput;
        private long tokensReasoning;
        private long durationTotalMs;
        private long durationRunCount;
        private Instant firstActivityAt;
        private Instant lastActivityAt;

        private ActivityAccumulator(
                Instant bucketStart,
                LocalDate activityDate,
                String userId,
                String username,
                String organization,
                String rdDepartment,
                String department,
                String workspaceId,
                String agentId,
                String modelId) {
            this.bucketStart = bucketStart;
            this.activityDate = activityDate;
            this.userId = valueOr(userId, "__unknown__");
            this.username = username;
            this.organization = valueOr(organization, "未归属");
            this.rdDepartment = valueOr(rdDepartment, "未归属");
            this.department = valueOr(department, "未归属");
            this.workspaceId = valueOr(workspaceId, "__none__");
            this.agentId = valueOr(agentId, "__none__");
            this.modelId = valueOr(modelId, "__none__");
        }

        static ActivityAccumulator from(AnalyticsModels.RawActivityRow row) {
            return new ActivityAccumulator(
                    row.occurredAt().truncatedTo(ChronoUnit.HOURS),
                    null,
                    row.userId(),
                    row.username(),
                    row.organization(),
                    row.rdDepartment(),
                    row.department(),
                    row.workspaceId(),
                    row.agentId(),
                    row.modelId());
        }

        static ActivityAccumulator from(AnalyticsModels.DurationSample row) {
            return new ActivityAccumulator(
                    row.bucketStart().truncatedTo(ChronoUnit.HOURS),
                    null,
                    row.userId(),
                    row.username(),
                    row.organization(),
                    row.rdDepartment(),
                    row.department(),
                    row.workspaceId(),
                    row.agentId(),
                    row.modelId());
        }

        static ActivityAccumulator from(LocalDate activityDate, AnalyticsModels.ActivityRollupRow row) {
            return new ActivityAccumulator(
                    null,
                    activityDate,
                    row.userId(),
                    row.username(),
                    row.organization(),
                    row.rdDepartment(),
                    row.department(),
                    row.workspaceId(),
                    row.agentId(),
                    row.modelId());
        }

        void add(AnalyticsModels.RawActivityRow row) {
            mergeIdentity(row.username(), row.organization(), row.rdDepartment(), row.department());
            loginCount += row.loginCount();
            sessionCount += row.sessionCount();
            activeSessionCount += row.activeSessionCount();
            emptySessionCount += row.emptySessionCount();
            continuousSessionCount += row.continuousSessionCount();
            userMessageCount += row.userMessageCount();
            assistantMessageCount += row.assistantMessageCount();
            runCount += row.runCount();
            succeededRunCount += row.succeededRunCount();
            failedRunCount += row.failedRunCount();
            cancelledRunCount += row.cancelledRunCount();
            activeTerminationCount += row.activeTerminationCount();
            validInteractionCount += row.validInteractionCount();
            positiveFeedbackCount += row.positiveFeedbackCount();
            negativeFeedbackCount += row.negativeFeedbackCount();
            diffProposedCount += row.diffProposedCount();
            diffAcceptedCount += row.diffAcceptedCount();
            diffRejectedCount += row.diffRejectedCount();
            tokensInput += row.tokensInput();
            tokensOutput += row.tokensOutput();
            tokensReasoning += row.tokensReasoning();
            markActivity(row.occurredAt());
        }

        void add(AnalyticsModels.ActivityRollupRow row) {
            mergeIdentity(row.username(), row.organization(), row.rdDepartment(), row.department());
            loginCount += row.loginCount();
            sessionCount += row.sessionCount();
            activeSessionCount += row.activeSessionCount();
            emptySessionCount += row.emptySessionCount();
            continuousSessionCount += row.continuousSessionCount();
            userMessageCount += row.userMessageCount();
            assistantMessageCount += row.assistantMessageCount();
            runCount += row.runCount();
            succeededRunCount += row.succeededRunCount();
            failedRunCount += row.failedRunCount();
            cancelledRunCount += row.cancelledRunCount();
            activeTerminationCount += row.activeTerminationCount();
            validInteractionCount += row.validInteractionCount();
            positiveFeedbackCount += row.positiveFeedbackCount();
            negativeFeedbackCount += row.negativeFeedbackCount();
            diffProposedCount += row.diffProposedCount();
            diffAcceptedCount += row.diffAcceptedCount();
            diffRejectedCount += row.diffRejectedCount();
            tokensInput += row.tokensInput();
            tokensOutput += row.tokensOutput();
            tokensReasoning += row.tokensReasoning();
            durationTotalMs += row.durationTotalMs();
            durationRunCount += row.durationRunCount();
            markActivity(row.firstActivityAt());
            markActivity(row.lastActivityAt());
        }

        void addDuration(long durationMs, Instant occurredAt) {
            durationTotalMs += durationMs;
            durationRunCount++;
            markActivity(occurredAt);
        }

        AnalyticsModels.ActivityRollupRow toHourlyRow() {
            return toRow(bucketStart, null);
        }

        AnalyticsModels.ActivityRollupRow toDailyRow() {
            return toRow(null, activityDate);
        }

        private AnalyticsModels.ActivityRollupRow toRow(Instant bucketStart, LocalDate activityDate) {
            return new AnalyticsModels.ActivityRollupRow(
                    bucketStart,
                    activityDate,
                    userId,
                    username,
                    organization,
                    rdDepartment,
                    department,
                    workspaceId,
                    agentId,
                    modelId,
                    loginCount,
                    sessionCount,
                    activeSessionCount,
                    emptySessionCount,
                    continuousSessionCount,
                    userMessageCount,
                    assistantMessageCount,
                    runCount,
                    succeededRunCount,
                    failedRunCount,
                    cancelledRunCount,
                    activeTerminationCount,
                    validInteractionCount,
                    positiveFeedbackCount,
                    negativeFeedbackCount,
                    diffProposedCount,
                    diffAcceptedCount,
                    diffRejectedCount,
                    tokensInput,
                    tokensOutput,
                    tokensReasoning,
                    tokensInput + tokensOutput + tokensReasoning,
                    durationTotalMs,
                    durationRunCount,
                    firstActivityAt,
                    lastActivityAt);
        }

        private void mergeIdentity(String username, String organization, String rdDepartment, String department) {
            if ((this.username == null || this.username.isBlank()) && username != null && !username.isBlank()) {
                this.username = username;
            }
            this.organization = better(this.organization, organization);
            this.rdDepartment = better(this.rdDepartment, rdDepartment);
            this.department = better(this.department, department);
        }

        private void markActivity(Instant occurredAt) {
            if (occurredAt == null) {
                return;
            }
            if (firstActivityAt == null || occurredAt.isBefore(firstActivityAt)) {
                firstActivityAt = occurredAt;
            }
            if (lastActivityAt == null || occurredAt.isAfter(lastActivityAt)) {
                lastActivityAt = occurredAt;
            }
        }

        private static String better(String current, String candidate) {
            if ((current == null || "未归属".equals(current)) && candidate != null && !candidate.isBlank()) {
                return candidate;
            }
            return valueOr(current, "未归属");
        }

        private static String valueOr(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
