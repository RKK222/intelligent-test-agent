package com.icbc.testagent.domain.analytics;

import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * AI 回复反馈领域对象，保存用户评分及提交时的组织快照。
 */
public record AiMessageFeedback(
        AiMessageFeedbackId feedbackId,
        UserId userId,
        SessionId sessionId,
        RunId runId,
        SessionMessageId messageId,
        AiMessageFeedbackRating rating,
        AiMessageFeedbackReasonCode reasonCode,
        String comment,
        String organization,
        String rdDepartment,
        String department,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 校验反馈字段，评论长度由领域层兜底限制。
     */
    public AiMessageFeedback {
        Objects.requireNonNull(feedbackId, "feedbackId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
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

    /**
     * 更新评分内容时保留首次创建时间和组织快照，避免用户调岗影响历史归属。
     */
    public AiMessageFeedback update(
            AiMessageFeedbackRating rating,
            AiMessageFeedbackReasonCode reasonCode,
            String comment,
            String traceId,
            Instant updatedAt) {
        return new AiMessageFeedback(
                feedbackId,
                userId,
                sessionId,
                runId,
                messageId,
                rating,
                reasonCode,
                comment,
                organization,
                rdDepartment,
                department,
                traceId,
                createdAt,
                updatedAt);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
