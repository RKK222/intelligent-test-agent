package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 运营分析 rollup 行模型，字段与小时/日汇总表保持一致。
 */
public record AnalyticsActivityRow(
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
        long tokensTotal,
        long durationTotalMs,
        long durationRunCount,
        Instant firstActivityAt,
        Instant lastActivityAt) {
}
