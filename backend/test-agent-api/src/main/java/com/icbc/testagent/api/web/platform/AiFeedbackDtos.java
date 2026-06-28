package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.domain.analytics.AiMessageFeedback;
import java.time.Instant;

/**
 * AI 回复满意度反馈 HTTP DTO，避免 Controller 直接暴露领域对象。
 */
final class AiFeedbackDtos {

    private AiFeedbackDtos() {
    }

    record FeedbackRequest(String rating, String reasonCode, String comment) {
    }

    record FeedbackResponse(
            String feedbackId,
            String messageId,
            String sessionId,
            String runId,
            String rating,
            String reasonCode,
            String comment,
            Instant createdAt,
            Instant updatedAt) {

        static FeedbackResponse from(AiMessageFeedback feedback) {
            return new FeedbackResponse(
                    feedback.feedbackId().value(),
                    feedback.messageId().value(),
                    feedback.sessionId().value(),
                    feedback.runId() == null ? null : feedback.runId().value(),
                    feedback.rating().name(),
                    feedback.reasonCode() == null ? null : feedback.reasonCode().name(),
                    feedback.comment(),
                    feedback.createdAt(),
                    feedback.updatedAt());
        }
    }
}
