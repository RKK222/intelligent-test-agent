package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.domain.analytics.AiRunFeedback;
import com.enterprise.testagent.opencode.runtime.analytics.RunFeedbackState;
import java.time.Instant;
import java.util.List;

/** Run 整体评价 HTTP DTO，不暴露历史 messageId。 */
final class AiRunFeedbackDtos {

    private AiRunFeedbackDtos() {
    }

    record FeedbackRequest(String rating, String reasonCode, String comment) {
    }

    record FeedbackResponse(
            String feedbackId,
            String runId,
            String sessionId,
            String rating,
            String reasonCode,
            String comment,
            Instant createdAt,
            Instant updatedAt) {

        static FeedbackResponse from(AiRunFeedback feedback) {
            return new FeedbackResponse(
                    feedback.feedbackId().value(), feedback.runId().value(), feedback.sessionId().value(),
                    feedback.rating().name(),
                    feedback.reasonCode() == null ? null : feedback.reasonCode().name(), feedback.comment(),
                    feedback.createdAt(), feedback.updatedAt());
        }
    }

    record FeedbackQueryRequest(List<String> runIds) {
    }

    record FeedbackStateResponse(
            String runId,
            String sessionId,
            String runStatus,
            FeedbackResponse feedback) {

        static FeedbackStateResponse from(RunFeedbackState state) {
            return new FeedbackStateResponse(
                    state.runId().value(), state.sessionId().value(), state.status().name(),
                    state.feedback().map(FeedbackResponse::from).orElse(null));
        }
    }
}
