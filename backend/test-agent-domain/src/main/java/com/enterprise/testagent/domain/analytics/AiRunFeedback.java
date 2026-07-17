package com.enterprise.testagent.domain.analytics;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * 用户对一次主智能体 Run 整体回复的评价，不依赖任何单条 assistant 消息。
 */
public record AiRunFeedback(
        AiMessageFeedbackId feedbackId,
        UserId userId,
        SessionId sessionId,
        RunId runId,
        AiMessageFeedbackRating rating,
        AiMessageFeedbackReasonCode reasonCode,
        String comment,
        String organization,
        String rdDepartment,
        String department,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {

    /** 校验反馈事实字段，并统一空备注与组织快照。 */
    public AiRunFeedback {
        Objects.requireNonNull(feedbackId, "feedbackId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(rating, "rating must not be null");
        comment = blankToNull(comment);
        if (comment != null && comment.length() > 300) {
            throw new IllegalArgumentException("comment length must be <= 300");
        }
        organization = blankToNull(organization);
        rdDepartment = blankToNull(rdDepartment);
        department = blankToNull(department);
        traceId = DomainValidation.requireText(traceId, "traceId");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    /** 更新同一用户、同一 Run 的评价，同时保留首次时间与组织快照。 */
    public AiRunFeedback update(
            AiMessageFeedbackRating nextRating,
            AiMessageFeedbackReasonCode nextReasonCode,
            String nextComment,
            String nextTraceId,
            Instant nextUpdatedAt) {
        return new AiRunFeedback(
                feedbackId,
                userId,
                sessionId,
                runId,
                nextRating,
                nextReasonCode,
                nextComment,
                organization,
                rdDepartment,
                department,
                nextTraceId,
                createdAt,
                nextUpdatedAt);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
