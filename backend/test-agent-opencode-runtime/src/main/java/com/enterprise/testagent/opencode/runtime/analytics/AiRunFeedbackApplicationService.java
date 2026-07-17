package com.enterprise.testagent.opencode.runtime.analytics;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackId;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackRating;
import com.enterprise.testagent.domain.analytics.AiMessageFeedbackReasonCode;
import com.enterprise.testagent.domain.analytics.AiRunFeedback;
import com.enterprise.testagent.domain.analytics.AiRunFeedbackRepository;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Run 整体评价应用服务：统一成功终态、归属、批量上限和单用户单 Run upsert 规则。
 */
@Service
public class AiRunFeedbackApplicationService {

    private static final int MAX_BATCH_SIZE = 100;

    private final AiRunFeedbackRepository feedbackRepository;
    private final RunRepository runRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    public AiRunFeedbackApplicationService(
            AiRunFeedbackRepository feedbackRepository,
            RunRepository runRepository,
            SessionRepository sessionRepository,
            UserRepository userRepository) {
        this.feedbackRepository = Objects.requireNonNull(feedbackRepository, "feedbackRepository must not be null");
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    /** 提交或更新一次成功 Run 的整体评价。 */
    public AiRunFeedback submitOrUpdate(
            UserId userId,
            RunId runId,
            String ratingValue,
            String reasonCodeValue,
            String comment,
            String traceId) {
        Run run = requireOwnedRun(userId, runId);
        if (run.status() != RunStatus.SUCCEEDED) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "Run 尚未成功完成，暂不能提交评价",
                    Map.of("runId", runId.value(), "runStatus", run.status().name()));
        }
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "用户不存在", Map.of("userId", userId.value())));
        AiMessageFeedbackRating rating = rating(ratingValue);
        AiMessageFeedbackReasonCode reasonCode = reasonCode(reasonCodeValue);
        String normalizedComment = normalizeComment(comment);
        Instant now = Instant.now();
        return feedbackRepository.findByUserIdAndRunId(userId, runId)
                .map(existing -> feedbackRepository.save(existing.update(rating, reasonCode, normalizedComment, traceId, now)))
                .orElseGet(() -> feedbackRepository.save(new AiRunFeedback(
                        new AiMessageFeedbackId(RuntimeIdGenerator.feedbackId()),
                        userId,
                        run.sessionId(),
                        runId,
                        rating,
                        reasonCode,
                        normalizedComment,
                        user.organization(),
                        user.rdDepartment(),
                        user.department(),
                        traceId,
                        now,
                        now)));
    }

    /** 查询当前用户对指定 Run 的评价，同时执行与提交一致的归属校验。 */
    public Optional<AiRunFeedback> findMyFeedback(UserId userId, RunId runId) {
        requireOwnedRun(userId, runId);
        return feedbackRepository.findByUserIdAndRunId(userId, runId);
    }

    /** 批量返回可见 Run 的真实状态和评价；输入保持顺序并去重。 */
    public List<RunFeedbackState> findMyFeedbackStates(UserId userId, List<RunId> requestedRunIds) {
        Objects.requireNonNull(userId, "userId must not be null");
        List<RunId> rawRunIds = requestedRunIds == null ? List.of() : requestedRunIds;
        if (rawRunIds.size() > MAX_BATCH_SIZE) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "单次最多查询 100 个 Run 的反馈状态",
                    Map.of("maxRunIds", MAX_BATCH_SIZE, "actualRunIds", rawRunIds.size()));
        }
        List<RunId> runIds = List.copyOf(new java.util.LinkedHashSet<>(rawRunIds));
        Map<RunId, Run> runs = new LinkedHashMap<>();
        runRepository.findByIds(runIds).forEach(run -> runs.put(run.runId(), run));
        for (RunId runId : runIds) {
            Run run = runs.get(runId);
            if (run == null) {
                throw new PlatformException(ErrorCode.NOT_FOUND, "Run 不存在", Map.of("runId", runId.value()));
            }
            requireMainConversationRun(run);
            requireOwnership(userId, run);
        }
        Map<RunId, AiRunFeedback> feedbacks = new LinkedHashMap<>();
        feedbackRepository.findByUserIdAndRunIds(userId, runIds)
                .forEach(feedback -> feedbacks.put(feedback.runId(), feedback));
        return runIds.stream()
                .map(runId -> {
                    Run run = runs.get(runId);
                    return new RunFeedbackState(runId, run.sessionId(), run.status(), Optional.ofNullable(feedbacks.get(runId)));
                })
                .toList();
    }

    private Run requireOwnedRun(UserId userId, RunId runId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Run 不存在", Map.of("runId", runId.value())));
        requireMainConversationRun(run);
        requireOwnership(userId, run);
        return run;
    }

    private void requireMainConversationRun(Run run) {
        if (run.sourceType() == ConversationSourceType.SIDE_QUESTION) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "旁路问答 Run 不提供整体评价",
                    Map.of("runId", run.runId().value(), "sourceType", run.sourceType().name()));
        }
    }

    private void requireOwnership(UserId userId, Run run) {
        if (userId.equals(run.triggeredByUserId())) {
            return;
        }
        Session session = sessionRepository.findById(run.sessionId())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "会话不存在", Map.of("sessionId", run.sessionId().value())));
        if (!userId.equals(session.createdByUserId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无权评价该 Run", Map.of("runId", run.runId().value()));
        }
    }

    private AiMessageFeedbackRating rating(String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "rating 不能为空");
        }
        try {
            return AiMessageFeedbackRating.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "rating 只能是 POSITIVE 或 NEGATIVE", Map.of("rating", value), exception);
        }
    }

    private AiMessageFeedbackReasonCode reasonCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AiMessageFeedbackReasonCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "reasonCode 不合法", Map.of("reasonCode", value), exception);
        }
    }

    private String normalizeComment(String comment) {
        String normalized = comment == null || comment.isBlank() ? null : comment.trim();
        if (normalized != null && normalized.length() > 300) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "comment 最多 300 字", Map.of("maxLength", 300));
        }
        return normalized;
    }
}
