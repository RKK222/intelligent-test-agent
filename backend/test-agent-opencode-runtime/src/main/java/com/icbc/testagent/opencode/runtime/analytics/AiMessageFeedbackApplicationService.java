package com.icbc.testagent.opencode.runtime.analytics;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.analytics.AiMessageFeedback;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackId;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackRating;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackReasonCode;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackRepository;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * AI 回复满意度反馈应用服务，负责反馈归属校验和单用户单消息 upsert。
 */
@Service
public class AiMessageFeedbackApplicationService {

    private final AiMessageFeedbackRepository feedbackRepository;
    private final SessionMessageRepository messageRepository;
    private final SessionRepository sessionRepository;
    private final RunRepository runRepository;
    private final UserRepository userRepository;

    public AiMessageFeedbackApplicationService(
            AiMessageFeedbackRepository feedbackRepository,
            SessionMessageRepository messageRepository,
            SessionRepository sessionRepository,
            RunRepository runRepository,
            UserRepository userRepository) {
        this.feedbackRepository = Objects.requireNonNull(feedbackRepository, "feedbackRepository must not be null");
        this.messageRepository = Objects.requireNonNull(messageRepository, "messageRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    /**
     * 提交或更新当前用户对 assistant 消息的反馈；主链路只写反馈事实，不刷新运营汇总。
     */
    public AiMessageFeedback submitOrUpdate(
            UserId userId,
            SessionMessageId messageId,
            String ratingValue,
            String reasonCodeValue,
            String comment,
            String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        AiMessageFeedbackRating rating = rating(ratingValue);
        AiMessageFeedbackReasonCode reasonCode = reasonCode(reasonCodeValue);
        String normalizedComment = normalizeComment(comment);
        OwnershipContext context = requireFeedbackOwnership(userId, messageId);
        Instant now = Instant.now();
        return feedbackRepository.findByUserIdAndMessageId(userId, messageId)
                .map(existing -> feedbackRepository.save(existing.update(
                        rating,
                        reasonCode,
                        normalizedComment,
                        traceId,
                        now)))
                .orElseGet(() -> feedbackRepository.save(new AiMessageFeedback(
                        new AiMessageFeedbackId(RuntimeIdGenerator.feedbackId()),
                        userId,
                        context.session().sessionId(),
                        context.message().runId(),
                        messageId,
                        rating,
                        reasonCode,
                        normalizedComment,
                        context.user().organization(),
                        context.user().rdDepartment(),
                        context.user().department(),
                        traceId,
                        now,
                        now)));
    }

    /**
     * 查询当前用户对指定 assistant 消息的反馈；同样先校验消息归属，避免状态探测。
     */
    public Optional<AiMessageFeedback> findMyFeedback(UserId userId, SessionMessageId messageId) {
        requireFeedbackOwnership(userId, messageId);
        return feedbackRepository.findByUserIdAndMessageId(userId, messageId);
    }

    private OwnershipContext requireFeedbackOwnership(UserId userId, SessionMessageId messageId) {
        SessionMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "消息不存在",
                        Map.of("messageId", messageId.value())));
        if (message.role() != SessionMessageRole.ASSISTANT) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "只能对 assistant 消息提交反馈",
                    Map.of("messageId", messageId.value(), "role", message.role().name()));
        }
        Session session = sessionRepository.findById(message.sessionId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "会话不存在",
                        Map.of("sessionId", message.sessionId().value())));
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "用户不存在",
                        Map.of("userId", userId.value())));
        if (!ownsMessage(userId, session, message)) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "无权反馈该消息",
                    Map.of("messageId", messageId.value()));
        }
        return new OwnershipContext(user, session, message);
    }

    private boolean ownsMessage(UserId userId, Session session, SessionMessage message) {
        boolean sessionOwner = userId.equals(session.createdByUserId());
        if (message.runId() == null) {
            return sessionOwner;
        }
        return runRepository.findById(message.runId())
                .map(Run::triggeredByUserId)
                .filter(userId::equals)
                .isPresent() || sessionOwner;
    }

    private AiMessageFeedbackRating rating(String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "rating 不能为空");
        }
        try {
            return AiMessageFeedbackRating.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "rating 只能是 POSITIVE 或 NEGATIVE",
                    Map.of("rating", value),
                    exception);
        }
    }

    private AiMessageFeedbackReasonCode reasonCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AiMessageFeedbackReasonCode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "reasonCode 不合法",
                    Map.of("reasonCode", value),
                    exception);
        }
    }

    private String normalizeComment(String comment) {
        String normalized = comment == null || comment.isBlank() ? null : comment.trim();
        if (normalized != null && normalized.length() > 300) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "comment 最多 300 字",
                    Map.of("maxLength", 300));
        }
        return normalized;
    }

    private record OwnershipContext(User user, Session session, SessionMessage message) {
    }
}
