package com.enterprise.testagent.domain.analytics;

import com.enterprise.testagent.common.pagination.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 运营分析对外查询模型集合，保持领域端口与 HTTP DTO、数据库行模型解耦。
 */
public final class AnalyticsModels {

    private AnalyticsModels() {
    }

    public enum Granularity {
        HOUR,
        DAY,
        WEEK,
        MONTH
    }

    public enum FreshnessStatus {
        FRESH,
        STALE,
        FAILED
    }

    public enum HeatmapMetric {
        USER_MESSAGES,
        PRIMARY_TOKENS,
        CACHE_TOKENS
    }

    public record Filter(
            Instant startTime,
            Instant endTime,
            Granularity granularity,
            String organization,
            String rdDepartment,
            String department,
            String userKeyword,
            String agentId,
            String model,
            String workspaceId,
            int topN,
            int page,
            int pageSize,
            String sort) {
    }

    public record Freshness(
            Instant generatedAt,
            FreshnessStatus status,
            String message,
            Instant coverageStart,
            Instant coverageEnd,
            String attributionMode) {

        public Freshness(Instant generatedAt, FreshnessStatus status, String message) {
            this(generatedAt, status, message, null, null, "EVENT_SNAPSHOT");
        }
    }

    public record FilterOption(String value, String label) {
    }

    public record FilterOptions(
            List<FilterOption> organizations,
            List<FilterOption> rdDepartments,
            List<FilterOption> departments,
            Freshness freshness) {
    }

    public record Funnel(
            long totalUsers,
            long activeUsers,
            long deepUsers,
            Double activeRate,
            Double deepRate,
            String activeDefinition,
            String deepDefinition,
            Freshness freshness) {
    }

    public record HourlyHeatmapPoint(LocalDate date, int hourOfDay, long value) {
    }

    public record HourlyHeatmap(
            HeatmapMetric metric,
            List<LocalDate> dates,
            List<HourlyHeatmapPoint> points,
            Freshness freshness) {
    }

    public record TokenDailyPoint(
            LocalDate date,
            long totalTokens,
            long primaryTokens,
            long cacheReadTokens,
            long cacheWriteTokens,
            long tokenUsers,
            Double tokensPerUser) {
    }

    public record TokenUserRow(
            String userId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            long totalTokens,
            long primaryTokens,
            long cacheReadTokens,
            long cacheWriteTokens,
            long tokenDays,
            Double tokensPerTokenDay,
            String intensityBand) {
    }

    public record TokenOperations(
            long totalTokens,
            long primaryTokens,
            long cacheReadTokens,
            long cacheWriteTokens,
            long tokenUsers,
            long activeUsers,
            Double tokenUserRate,
            long tokenActivePersonDays,
            Double dailyTokensPerUser,
            long repeatTokenUsers,
            Double repeatTokenUserRate,
            List<TokenDailyPoint> daily,
            List<TokenUserRow> users,
            Freshness freshness) {
    }

    public record CapabilityUsageRow(
            String type,
            String name,
            long invocationCount,
            long userCount,
            long succeededCount,
            long failedCount,
            long incompleteCount) {
    }

    public record CapabilityUsage(
            String type,
            String name,
            long invocationCount,
            long userCount,
            Double usageRate,
            long succeededCount,
            long failedCount,
            long incompleteCount) {
    }

    public record Capabilities(
            long activeUsers,
            List<CapabilityUsage> rows,
            Freshness freshness,
            String source,
            Instant coverageStartAt,
            Instant completeThrough,
            Double rolloutCompleteness) {

        public Capabilities(long activeUsers, List<CapabilityUsage> rows, Freshness freshness) {
            this(activeUsers, rows, freshness, "LEGACY_RUNEVENT", null, null, 0D);
        }
    }

    public record CapabilityCoverage(
            String source,
            Instant coverageStartAt,
            Instant completeThrough,
            Double rolloutCompleteness) {
    }

    public record Overview(
            long registeredUsers,
            long enabledUsers,
            long loginUsers,
            long activeUsers,
            long validUsers,
            long deepUsers,
            Double activeRate,
            Double loginToActiveRate,
            Double activeToValidRate,
            Double validToDeepRate,
            long sessionCount,
            long activeSessionCount,
            long emptySessionCount,
            long continuousSessionCount,
            long userMessageCount,
            long assistantMessageCount,
            long runCount,
            Double runsPerUser,
            Double messagesPerUser,
            Double messagesPerSession,
            Double continuousConversationRate,
            long validInteractionCount,
            long sustainedUsers,
            long succeededRuns,
            long failedRuns,
            long cancelledRuns,
            long activeTerminations,
            Double successRate,
            Double failureRate,
            Double cancellationRate,
            Long averageDurationMs,
            Long p95DurationMs,
            long positiveFeedbackCount,
            long negativeFeedbackCount,
            Double satisfactionRate,
            Double feedbackCoverageRate,
            long diffProposedCount,
            long diffAcceptedCount,
            long diffRejectedCount,
            Double diffAcceptanceRate,
            Double diffRejectionRate,
            long inputTokens,
            long outputTokens,
            long reasoningTokens,
            long totalTokens,
            Double tokensPerUser,
            Double tokensPerRun,
            Freshness freshness) {
    }

    public record TimeSeriesPoint(
            Instant bucketStart,
            long loginUsers,
            long activeUsers,
            long sessionCount,
            long activeSessionCount,
            long userMessageCount,
            long assistantMessageCount,
            long runCount,
            long succeededRuns,
            long failedRuns,
            long cancelledRuns,
            long positiveFeedbackCount,
            long negativeFeedbackCount,
            long diffAcceptedCount,
            long diffRejectedCount,
            long totalTokens,
            Double satisfactionRate,
            Double diffAcceptanceRate,
            Double cancellationRate) {
    }

    public record PeakPoint(
            Instant bucketStart,
            long activeUsers,
            long runCount,
            long userMessageCount,
            Double satisfactionRate,
            Double cancellationRate,
            long totalTokens) {
    }

    public record HeatmapPoint(int dayOfWeek, int hourOfDay, long activeUsers, long runCount, long userMessageCount) {
    }

    public record Peaks(List<PeakPoint> peakPeriods, List<HeatmapPoint> heatmap, Freshness freshness) {
    }

    public record UserUsageRow(
            String userId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            long loginCount,
            long sessionCount,
            long activeSessionCount,
            long userMessageCount,
            long runCount,
            long succeededRuns,
            long failedRuns,
            long cancelledRuns,
            long positiveFeedbackCount,
            long negativeFeedbackCount,
            long diffAcceptedCount,
            long diffRejectedCount,
            long totalTokens,
            Double successRate,
            Double satisfactionRate,
            Double diffAcceptanceRate,
            Instant lastActivityAt) {
    }

    public record OrganizationUsageRow(
            String dimension,
            String name,
            long registeredUsers,
            long enabledUsers,
            long loginUsers,
            long activeUsers,
            long deepUsers,
            Double activeRate,
            Double deepRate,
            long runCount,
            long succeededRuns,
            long failedRuns,
            long cancelledRuns,
            long positiveFeedbackCount,
            long negativeFeedbackCount,
            long diffAcceptedCount,
            long diffRejectedCount,
            long totalTokens,
            Double successRate,
            Double satisfactionRate,
            Double diffAcceptanceRate) {
    }

    public record FeedbackDetail(
            String feedbackId,
            String userId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            String sessionId,
            String runId,
            String messageId,
            AiMessageFeedbackRating rating,
            AiMessageFeedbackReasonCode reasonCode,
            String comment,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ExceptionDetail(
            String runId,
            String userId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            String workspaceId,
            String agentId,
            String modelId,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record Satisfaction(
            long positiveFeedbackCount,
            long negativeFeedbackCount,
            Double satisfactionRate,
            Double feedbackCoverageRate,
            Map<String, Long> negativeReasonCounts,
            PageResponse<FeedbackDetail> feedbackDetails,
            Freshness freshness) {
    }

    public record RawActivityRow(
            Instant occurredAt,
            String userId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            String workspaceId,
            String agentId,
            String modelId,
            long loginCount,
            long sessionCount,
            long activeSessionCount,
            long emptySessionCount,
            long continuousSessionCount,
            long userMessageCount,
            long assistantMessageCount,
            long runCount,
            long succeededRunCount,
            long failedRunCount,
            long cancelledRunCount,
            long activeTerminationCount,
            long validInteractionCount,
            long positiveFeedbackCount,
            long negativeFeedbackCount,
            long diffProposedCount,
            long diffAcceptedCount,
            long diffRejectedCount,
            long tokensInput,
            long tokensOutput,
            long tokensReasoning,
            long tokensCacheRead,
            long tokensCacheWrite,
            long durationMs) {
    }

    public record ActivityRollupRow(
            Instant bucketStart,
            LocalDate activityDate,
            String userId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            String workspaceId,
            String agentId,
            String modelId,
            long loginCount,
            long sessionCount,
            long activeSessionCount,
            long emptySessionCount,
            long continuousSessionCount,
            long userMessageCount,
            long assistantMessageCount,
            long runCount,
            long succeededRunCount,
            long failedRunCount,
            long cancelledRunCount,
            long activeTerminationCount,
            long validInteractionCount,
            long positiveFeedbackCount,
            long negativeFeedbackCount,
            long diffProposedCount,
            long diffAcceptedCount,
            long diffRejectedCount,
            long tokensInput,
            long tokensOutput,
            long tokensReasoning,
            long tokensCacheRead,
            long tokensCacheWrite,
            long tokensTotal,
            long durationTotalMs,
            long durationRunCount,
            Instant firstActivityAt,
            Instant lastActivityAt) {
    }

    public record DurationSample(
            Instant bucketStart,
            String userId,
            String username,
            String organization,
            String rdDepartment,
            String department,
            String workspaceId,
            String agentId,
            String modelId,
            long durationMs) {
    }

    public record DurationHistogramRow(
            Instant bucketStart,
            String organization,
            String rdDepartment,
            String department,
            String workspaceId,
            String agentId,
            String modelId,
            long leMs,
            long runCount) {
    }
}
