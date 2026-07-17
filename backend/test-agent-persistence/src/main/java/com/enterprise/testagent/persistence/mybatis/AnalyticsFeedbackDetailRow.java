package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 满意度反馈明细查询行，不包含 prompt 或 assistant 正文。
 */
public record AnalyticsFeedbackDetailRow(
        String feedbackId,
        String userId,
        String username,
        String organization,
        String rdDepartment,
        String department,
        String sessionId,
        String runId,
        String messageId,
        String rating,
        String reasonCode,
        String comment,
        Instant createdAt,
        Instant updatedAt) {
}
