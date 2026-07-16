package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.analytics.AiMessageFeedbackId;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackRating;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackReasonCode;
import com.enterprise.testagent.domain.analytics.AiRunFeedback;
import com.enterprise.testagent.domain.analytics.AiRunFeedbackRepository;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Run 级 AI 反馈 Repository 的 MyBatis XML 实现。 */
@Repository
public class MyBatisAiRunFeedbackRepository implements AiRunFeedbackRepository {

    private final AiRunFeedbackMapper mapper;

    public MyBatisAiRunFeedbackRepository(AiRunFeedbackMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public AiRunFeedback save(AiRunFeedback feedback) {
        AiMessageFeedbackRow row = toRow(feedback);
        if (mapper.findByFeedbackId(feedback.feedbackId().value()) == null) {
            mapper.insert(row);
        } else {
            mapper.update(row);
        }
        return feedback;
    }

    @Override
    public Optional<AiRunFeedback> findByUserIdAndRunId(UserId userId, RunId runId) {
        return Optional.ofNullable(mapper.findByUserIdAndRunId(userId.value(), runId.value()))
                .map(this::toDomain);
    }

    @Override
    public List<AiRunFeedback> findByUserIdAndRunIds(UserId userId, List<RunId> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return List.of();
        }
        return mapper.findByUserIdAndRunIds(
                        userId.value(), runIds.stream().map(RunId::value).distinct().toList()).stream()
                .map(this::toDomain)
                .toList();
    }

    private AiMessageFeedbackRow toRow(AiRunFeedback feedback) {
        return new AiMessageFeedbackRow(
                feedback.feedbackId().value(), feedback.userId().value(), feedback.sessionId().value(),
                feedback.runId().value(), null, feedback.rating().name(),
                feedback.reasonCode() == null ? null : feedback.reasonCode().name(), feedback.comment(),
                feedback.organization(), feedback.rdDepartment(), feedback.department(), feedback.traceId(),
                feedback.createdAt(), feedback.updatedAt());
    }

    private AiRunFeedback toDomain(AiMessageFeedbackRow row) {
        return new AiRunFeedback(
                new AiMessageFeedbackId(row.feedbackId()), new UserId(row.userId()),
                new SessionId(row.sessionId()), new RunId(row.runId()),
                AiMessageFeedbackRating.valueOf(row.rating()),
                row.reasonCode() == null ? null : AiMessageFeedbackReasonCode.valueOf(row.reasonCode()),
                row.comment(), row.organization(), row.rdDepartment(), row.department(), row.traceId(),
                row.createdAt(), row.updatedAt());
    }
}
