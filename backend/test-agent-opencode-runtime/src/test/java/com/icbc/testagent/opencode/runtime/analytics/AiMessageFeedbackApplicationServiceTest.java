package com.icbc.testagent.opencode.runtime.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.analytics.AiMessageFeedback;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackRating;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackReasonCode;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackRepository;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import com.icbc.testagent.domain.user.UserStatus;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiMessageFeedbackApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");
    private static final UserId OWNER = new UserId("usr_owner1234567890");
    private static final UserId OTHER_USER = new UserId("usr_other1234567890");
    private static final SessionId SESSION_ID = new SessionId("ses_feedback123456");
    private static final RunId RUN_ID = new RunId("run_feedback123456");
    private static final SessionMessageId ASSISTANT_MESSAGE_ID = new SessionMessageId("msg_assistant123456");
    private static final SessionMessageId USER_MESSAGE_ID = new SessionMessageId("msg_user1234567890");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_feedback123456");

    @Test
    void submitCreatesAndUpdatesOwnAssistantFeedback() {
        FakeFeedbackRepository feedbacks = new FakeFeedbackRepository();
        AiMessageFeedbackApplicationService service = service(feedbacks, Map.of(
                ASSISTANT_MESSAGE_ID, assistantMessage()), session(OWNER), run(OWNER), users());

        AiMessageFeedback created = service.submitOrUpdate(
                OWNER,
                ASSISTANT_MESSAGE_ID,
                "POSITIVE",
                null,
                " 有帮助 ",
                "trace_feedback123456");
        AiMessageFeedback updated = service.submitOrUpdate(
                OWNER,
                ASSISTANT_MESSAGE_ID,
                "NEGATIVE",
                "WRONG_ANSWER",
                "结论不对",
                "trace_feedback654321");

        assertThat(created.feedbackId()).isEqualTo(updated.feedbackId());
        assertThat(updated.rating()).isEqualTo(AiMessageFeedbackRating.NEGATIVE);
        assertThat(updated.reasonCode()).isEqualTo(AiMessageFeedbackReasonCode.WRONG_ANSWER);
        assertThat(updated.comment()).isEqualTo("结论不对");
        assertThat(updated.organization()).isEqualTo("总行");
        assertThat(feedbacks.saved).hasSize(2);
    }

    @Test
    void submitRejectsNonAssistantMessage() {
        AiMessageFeedbackApplicationService service = service(new FakeFeedbackRepository(), Map.of(
                USER_MESSAGE_ID, userMessage()), session(OWNER), run(OWNER), users());

        assertThatThrownBy(() -> service.submitOrUpdate(
                        OWNER,
                        USER_MESSAGE_ID,
                        "POSITIVE",
                        null,
                        null,
                        "trace_feedback123456"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void submitRejectsMessageOutsideCurrentUserOwnership() {
        AiMessageFeedbackApplicationService service = service(new FakeFeedbackRepository(), Map.of(
                ASSISTANT_MESSAGE_ID, assistantMessage()), session(OWNER), run(OWNER), users());

        assertThatThrownBy(() -> service.submitOrUpdate(
                        OTHER_USER,
                        ASSISTANT_MESSAGE_ID,
                        "NEGATIVE",
                        "OTHER",
                        null,
                        "trace_feedback123456"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void submitRejectsCommentLongerThanLimit() {
        AiMessageFeedbackApplicationService service = service(new FakeFeedbackRepository(), Map.of(
                ASSISTANT_MESSAGE_ID, assistantMessage()), session(OWNER), run(OWNER), users());

        assertThatThrownBy(() -> service.submitOrUpdate(
                        OWNER,
                        ASSISTANT_MESSAGE_ID,
                        "NEGATIVE",
                        "OTHER",
                        "x".repeat(301),
                        "trace_feedback123456"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private static AiMessageFeedbackApplicationService service(
            AiMessageFeedbackRepository feedbacks,
            Map<SessionMessageId, SessionMessage> messages,
            Session session,
            Run run,
            Map<UserId, User> users) {
        return new AiMessageFeedbackApplicationService(
                feedbacks,
                new FakeMessageRepository(messages),
                new FakeSessionRepository(session),
                new FakeRunRepository(run),
                new FakeUserRepository(users));
    }

    private static Session session(UserId owner) {
        return new Session(
                SESSION_ID,
                WORKSPACE_ID,
                "Feedback",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_feedback123456")
                .withSource(ConversationSourceType.MANUAL, null, owner);
    }

    private static Run run(UserId owner) {
        return new Run(
                RUN_ID,
                SESSION_ID,
                WORKSPACE_ID,
                RunStatus.SUCCEEDED,
                NOW,
                NOW.plusSeconds(5),
                "trace_feedback123456")
                .withSource(ConversationSourceType.MANUAL, null, owner);
    }

    private static SessionMessage assistantMessage() {
        return new SessionMessage(
                ASSISTANT_MESSAGE_ID,
                SESSION_ID,
                SessionMessageRole.ASSISTANT,
                "done",
                NOW,
                "trace_feedback123456",
                RUN_ID,
                "opencode",
                "remote_assistant_1",
                null,
                null,
                null,
                NOW);
    }

    private static SessionMessage userMessage() {
        return new SessionMessage(
                USER_MESSAGE_ID,
                SESSION_ID,
                SessionMessageRole.USER,
                "please run",
                NOW,
                "trace_feedback123456");
    }

    private static Map<UserId, User> users() {
        return Map.of(
                OWNER, user(OWNER, "owner"),
                OTHER_USER, user(OTHER_USER, "other"));
    }

    private static User user(UserId userId, String username) {
        return new User(
                userId,
                "u_" + username,
                username,
                "{noop}password",
                "总行",
                "研发一部",
                "效能平台",
                UserStatus.ACTIVE,
                NOW,
                NOW);
    }

    private static final class FakeFeedbackRepository implements AiMessageFeedbackRepository {
        private final Map<String, AiMessageFeedback> feedbacks = new HashMap<>();
        private final List<AiMessageFeedback> saved = new ArrayList<>();

        @Override
        public AiMessageFeedback save(AiMessageFeedback feedback) {
            feedbacks.put(key(feedback.userId(), feedback.messageId()), feedback);
            saved.add(feedback);
            return feedback;
        }

        @Override
        public Optional<AiMessageFeedback> findByUserIdAndMessageId(UserId userId, SessionMessageId messageId) {
            return Optional.ofNullable(feedbacks.get(key(userId, messageId)));
        }

        private static String key(UserId userId, SessionMessageId messageId) {
            return userId.value() + ":" + messageId.value();
        }
    }

    private record FakeMessageRepository(Map<SessionMessageId, SessionMessage> messages) implements SessionMessageRepository {
        @Override
        public SessionMessage save(SessionMessage message) {
            return message;
        }

        @Override
        public Optional<SessionMessage> findById(SessionMessageId messageId) {
            return Optional.ofNullable(messages.get(messageId));
        }

        @Override
        public PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest) {
            List<SessionMessage> items = messages.values().stream()
                    .filter(message -> message.sessionId().equals(sessionId))
                    .toList();
            return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), items.size());
        }
    }

    private record FakeSessionRepository(Session session) implements SessionRepository {
        @Override
        public Session save(Session session) {
            return session;
        }

        @Override
        public Optional<Session> findById(SessionId sessionId) {
            return session.sessionId().equals(sessionId) ? Optional.of(session) : Optional.empty();
        }

        @Override
        public PageResponse<Session> findPage(String query, PageRequest pageRequest) {
            return new PageResponse<>(List.of(session), pageRequest.page(), pageRequest.size(), 1);
        }

        @Override
        public PageResponse<Session> findByWorkspaceId(WorkspaceId workspaceId, PageRequest pageRequest) {
            return new PageResponse<>(List.of(session), pageRequest.page(), pageRequest.size(), 1);
        }

        @Override
        public Optional<Session> attachOpencodeSession(
                SessionId sessionId,
                String opencodeSessionId,
                ExecutionNodeId executionNodeId,
                Instant updatedAt,
                String traceId) {
            return Optional.empty();
        }
    }

    private record FakeRunRepository(Run run) implements RunRepository {
        @Override
        public Run save(Run run) {
            return run;
        }

        @Override
        public Optional<Run> findById(RunId runId) {
            return run.runId().equals(runId) ? Optional.of(run) : Optional.empty();
        }
    }

    private record FakeUserRepository(Map<UserId, User> users) implements UserRepository {
        @Override
        public void save(User user) {
        }

        @Override
        public Optional<User> findByUserId(UserId userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Optional<User> findByUnifiedAuthId(String unifiedAuthId) {
            return users.values().stream()
                    .filter(user -> user.unifiedAuthId().equals(unifiedAuthId))
                    .findFirst();
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return users.values().stream()
                    .filter(user -> user.username().equals(username))
                    .findFirst();
        }

        @Override
        public PageResponse<User> findPage(String keyword, PageRequest pageRequest) {
            List<User> items = List.copyOf(users.values());
            return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), items.size());
        }

        @Override
        public boolean existsByUsername(String username) {
            return findByUsername(username).isPresent();
        }

        @Override
        public boolean existsByUnifiedAuthId(String unifiedAuthId) {
            return findByUnifiedAuthId(unifiedAuthId).isPresent();
        }
    }
}
