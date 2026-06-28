package com.icbc.testagent.persistence.mybatis;

import com.icbc.testagent.domain.analytics.AiMessageFeedback;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackId;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackRating;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackReasonCode;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackRepository;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.user.UserId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * AI 消息反馈 Repository 的 MyBatis XML 实现。
 */
@Repository
public class MyBatisAiMessageFeedbackRepository implements AiMessageFeedbackRepository {

    private final AiMessageFeedbackMapper mapper;

    public MyBatisAiMessageFeedbackRepository(AiMessageFeedbackMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public AiMessageFeedback save(AiMessageFeedback feedback) {
        AiMessageFeedbackRow row = toRow(feedback);
        AiMessageFeedbackRow existing = mapper.findByFeedbackId(feedback.feedbackId().value());
        if (existing == null) {
            mapper.insert(row);
        } else {
            mapper.update(row);
        }
        return feedback;
    }

    @Override
    public Optional<AiMessageFeedback> findByUserIdAndMessageId(UserId userId, SessionMessageId messageId) {
        return Optional.ofNullable(mapper.findByUserIdAndMessageId(userId.value(), messageId.value()))
                .map(this::toDomain);
    }

    private AiMessageFeedbackRow toRow(AiMessageFeedback feedback) {
        return new AiMessageFeedbackRow(
                feedback.feedbackId().value(),
                feedback.userId().value(),
                feedback.sessionId().value(),
                feedback.runId() == null ? null : feedback.runId().value(),
                feedback.messageId().value(),
                feedback.rating().name(),
                feedback.reasonCode() == null ? null : feedback.reasonCode().name(),
                feedback.comment(),
                feedback.organization(),
                feedback.rdDepartment(),
                feedback.department(),
                feedback.traceId(),
                feedback.createdAt(),
                feedback.updatedAt());
    }

    private AiMessageFeedback toDomain(AiMessageFeedbackRow row) {
        return new AiMessageFeedback(
                new AiMessageFeedbackId(row.feedbackId()),
                new UserId(row.userId()),
                new SessionId(row.sessionId()),
                row.runId() == null ? null : new RunId(row.runId()),
                new SessionMessageId(row.messageId()),
                AiMessageFeedbackRating.valueOf(row.rating()),
                row.reasonCode() == null ? null : AiMessageFeedbackReasonCode.valueOf(row.reasonCode()),
                row.comment(),
                row.organization(),
                row.rdDepartment(),
                row.department(),
                row.traceId(),
                row.createdAt(),
                row.updatedAt());
    }
}
