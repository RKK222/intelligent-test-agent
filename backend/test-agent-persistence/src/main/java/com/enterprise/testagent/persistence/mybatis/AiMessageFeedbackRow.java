package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * ai_message_feedbacks 表行模型，仅在 MyBatis 持久层内部使用。
 */
public record AiMessageFeedbackRow(
        String feedbackId,
        String userId,
        String sessionId,
        String runId,
        String messageId,
        String rating,
        String reasonCode,
        String comment,
        String organization,
        String rdDepartment,
        String department,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {
}
