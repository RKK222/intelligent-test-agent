package com.icbc.testagent.opencode.runtime.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.analytics.AiRunFeedback;
import com.icbc.testagent.domain.analytics.AiRunFeedbackRepository;
import com.icbc.testagent.domain.analytics.AiMessageFeedbackRating;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
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

class AiRunFeedbackApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T13:00:00Z");
    private static final UserId OWNER = new UserId("usr_run_feedback_owner");
    private static final UserId OTHER = new UserId("usr_run_feedback_other");
    private static final SessionId SESSION_ID = new SessionId("ses_run_feedback");
    private static final RunId RUN_ID = new RunId("run_0123456789abcdef0123456789abcdef");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_run_feedback");

    @Test
    void successfulRunFeedbackIsUpsertedByUserAndRun() {
        FakeFeedbackRepository feedbacks = new FakeFeedbackRepository();
        AiRunFeedbackApplicationService service = service(feedbacks, run(RunStatus.SUCCEEDED, OWNER), session(OWNER));

        AiRunFeedback created = service.submitOrUpdate(
                OWNER, RUN_ID, "POSITIVE", null, null, "trace_run_feedback_create");
        AiRunFeedback updated = service.submitOrUpdate(
                OWNER, RUN_ID, "NEGATIVE", "WRONG_ANSWER", "整体回复有误", "trace_run_feedback_update");

        assertThat(updated.feedbackId()).isEqualTo(created.feedbackId());
        assertThat(updated.runId()).isEqualTo(RUN_ID);
        assertThat(updated.rating()).isEqualTo(AiMessageFeedbackRating.NEGATIVE);
        assertThat(updated.comment()).isEqualTo("整体回复有误");
        assertThat(feedbacks.saved).hasSize(2);
    }

    @Test
    void feedbackRejectsRunThatHasNotSucceeded() {
        AiRunFeedbackApplicationService service = service(
                new FakeFeedbackRepository(), run(RunStatus.RUNNING, OWNER), session(OWNER));

        assertThatThrownBy(() -> service.submitOrUpdate(
                OWNER, RUN_ID, "POSITIVE", null, null, "trace_run_feedback_conflict"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("runStatus", "RUNNING");
                });
    }

    @Test
    void feedbackRejectsRunOutsideCurrentUserOwnership() {
        AiRunFeedbackApplicationService service = service(
                new FakeFeedbackRepository(), run(RunStatus.SUCCEEDED, OWNER), session(OWNER));

        assertThatThrownBy(() -> service.submitOrUpdate(
                OTHER, RUN_ID, "POSITIVE", null, null, "trace_run_feedback_forbidden"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void feedbackRejectsSideQuestionRun() {
        Run sideQuestion = run(RunStatus.SUCCEEDED, OWNER)
                .withSource(ConversationSourceType.SIDE_QUESTION, SESSION_ID.value(), OWNER);
        AiRunFeedbackApplicationService service = service(
                new FakeFeedbackRepository(), sideQuestion, session(OWNER));

        assertThatThrownBy(() -> service.submitOrUpdate(
                OWNER, RUN_ID, "POSITIVE", null, null, "trace_run_feedback_side"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void batchQueryReturnsRunStatusAndFeedbackAndRejectsMoreThanOneHundredIds() {
        FakeFeedbackRepository feedbacks = new FakeFeedbackRepository();
        AiRunFeedbackApplicationService service = service(feedbacks, run(RunStatus.SUCCEEDED, OWNER), session(OWNER));
        AiRunFeedback saved = service.submitOrUpdate(
                OWNER, RUN_ID, "POSITIVE", null, null, "trace_run_feedback_batch");

        List<RunFeedbackState> states = service.findMyFeedbackStates(OWNER, List.of(RUN_ID));

        assertThat(states).singleElement().satisfies(state -> {
            assertThat(state.runId()).isEqualTo(RUN_ID);
            assertThat(state.status()).isEqualTo(RunStatus.SUCCEEDED);
            assertThat(state.feedback()).contains(saved);
        });
        List<RunId> tooMany = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> new RunId("run_batch_" + index))
                .toList();
        assertThatThrownBy(() -> service.findMyFeedbackStates(OWNER, tooMany))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private static AiRunFeedbackApplicationService service(
            AiRunFeedbackRepository feedbacks,
            Run run,
            Session session) {
        return new AiRunFeedbackApplicationService(
                feedbacks,
                new FakeRunRepository(run),
                new FakeSessionRepository(session),
                new FakeUserRepository(Map.of(OWNER, user(OWNER), OTHER, user(OTHER))));
    }

    private static Run run(RunStatus status, UserId trigger) {
        return new Run(RUN_ID, SESSION_ID, WORKSPACE_ID, status, NOW, NOW.plusSeconds(1), "trace_run_feedback")
                .withSource(ConversationSourceType.MANUAL, null, trigger);
    }

    private static Session session(UserId owner) {
        return new Session(SESSION_ID, WORKSPACE_ID, "Run feedback", SessionStatus.ACTIVE, NOW, NOW, "trace_run_feedback")
                .withSource(ConversationSourceType.MANUAL, null, owner);
    }

    private static User user(UserId id) {
        return new User(id, "auth_" + id.value(), id.value(), "{noop}password", "总行", "研发部", "团队", UserStatus.ACTIVE, NOW, NOW);
    }

    private static final class FakeFeedbackRepository implements AiRunFeedbackRepository {
        private final Map<String, AiRunFeedback> feedbacks = new HashMap<>();
        private final List<AiRunFeedback> saved = new ArrayList<>();

        @Override
        public AiRunFeedback save(AiRunFeedback feedback) {
            feedbacks.put(key(feedback.userId(), feedback.runId()), feedback);
            saved.add(feedback);
            return feedback;
        }

        @Override
        public Optional<AiRunFeedback> findByUserIdAndRunId(UserId userId, RunId runId) {
            return Optional.ofNullable(feedbacks.get(key(userId, runId)));
        }

        @Override
        public List<AiRunFeedback> findByUserIdAndRunIds(UserId userId, List<RunId> runIds) {
            return runIds.stream().map(runId -> feedbacks.get(key(userId, runId))).filter(java.util.Objects::nonNull).toList();
        }

        private static String key(UserId userId, RunId runId) {
            return userId.value() + ":" + runId.value();
        }
    }

    private record FakeRunRepository(Run run) implements RunRepository {
        @Override public Run save(Run value) { return value; }
        @Override public Optional<Run> findById(RunId runId) { return run.runId().equals(runId) ? Optional.of(run) : Optional.empty(); }
        @Override public List<Run> findByIds(List<RunId> runIds) { return runIds.contains(run.runId()) ? List.of(run) : List.of(); }
    }

    private record FakeSessionRepository(Session session) implements SessionRepository {
        @Override public Session save(Session value) { return value; }
        @Override public Optional<Session> findById(SessionId sessionId) { return session.sessionId().equals(sessionId) ? Optional.of(session) : Optional.empty(); }
        @Override public PageResponse<Session> findPage(String query, PageRequest request) { return new PageResponse<>(List.of(session), request.page(), request.size(), 1); }
        @Override public PageResponse<Session> findByWorkspaceId(WorkspaceId workspaceId, PageRequest request) { return new PageResponse<>(List.of(session), request.page(), request.size(), 1); }
        @Override public Optional<Session> attachOpencodeSession(SessionId sessionId, String opencodeSessionId, ExecutionNodeId nodeId, Instant updatedAt, String traceId) { return Optional.empty(); }
    }

    private record FakeUserRepository(Map<UserId, User> users) implements UserRepository {
        @Override public void save(User user) { }
        @Override public Optional<User> findByUserId(UserId userId) { return Optional.ofNullable(users.get(userId)); }
        @Override public Optional<User> findByUnifiedAuthId(String value) { return Optional.empty(); }
        @Override public Optional<User> findByUsername(String value) { return Optional.empty(); }
        @Override public PageResponse<User> findPage(String keyword, PageRequest request) { return new PageResponse<>(List.copyOf(users.values()), request.page(), request.size(), users.size()); }
        @Override public boolean existsByUsername(String value) { return false; }
        @Override public boolean existsByUnifiedAuthId(String value) { return false; }
    }
}
