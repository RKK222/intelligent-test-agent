package com.icbc.testagent.opencode.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_1234567890abcdef");
    private static final SessionId SESSION_ID = new SessionId("ses_1234567890abcdef");

    @Test
    void createsSessionOnlyWhenWorkspaceExists() {
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        SessionApplicationService service = service(new FakeWorkspaceRepository(true), sessions, new FakeMessageRepository());

        Session created = service.createSession(WORKSPACE_ID, "Demo session", "trace_1234567890abcdef");

        assertThat(created.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(created.title()).isEqualTo("Demo session");
        assertThat(created.status()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(sessions.saved).singleElement().satisfies(saved ->
                assertThat(saved.traceId()).isEqualTo("trace_1234567890abcdef"));
    }

    @Test
    void createSessionWithUserRecordsCreator() {
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        SessionApplicationService service = service(new FakeWorkspaceRepository(true), sessions, new FakeMessageRepository());

        Session created = service.createSession(
                new UserId("usr_1234567890abcdef"),
                WORKSPACE_ID,
                "Demo session",
                "trace_1234567890abcdef");

        assertThat(created.createdByUserId()).isEqualTo(new UserId("usr_1234567890abcdef"));
        assertThat(sessions.saved).singleElement().satisfies(saved ->
                assertThat(saved.createdByUserId()).isEqualTo(new UserId("usr_1234567890abcdef")));
    }

    @Test
    void createSessionFailsWhenWorkspaceIsMissing() {
        SessionApplicationService service = service(new FakeWorkspaceRepository(false), new FakeSessionRepository(session()), new FakeMessageRepository());

        assertThatThrownBy(() -> service.createSession(WORKSPACE_ID, "Demo session", "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void archivedSessionIsHiddenFromGetSession() {
        Session archived = session().archive(NOW.plusSeconds(1), "trace_1234567890abcdef");
        SessionApplicationService service = service(new FakeWorkspaceRepository(true), new FakeSessionRepository(archived), new FakeMessageRepository());

        assertThatThrownBy(() -> service.getSession(SESSION_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void updateSessionPreservesMissingFields() {
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        SessionApplicationService service = service(new FakeWorkspaceRepository(true), sessions, new FakeMessageRepository());

        Session updated = service.updateSession(SESSION_ID, " ", true, "trace_1234567890abcdef");

        assertThat(updated.title()).isEqualTo("Demo session");
        assertThat(updated.pinned()).isTrue();
        assertThat(sessions.saved.getLast()).isEqualTo(updated);
    }

    @Test
    void appendMessageDefaultsRoleToUserAndRequiresActiveSession() {
        FakeMessageRepository messages = new FakeMessageRepository();
        SessionApplicationService service = service(new FakeWorkspaceRepository(true), new FakeSessionRepository(session()), messages);

        SessionMessage message = service.appendMessage(SESSION_ID, null, "run tests", "trace_1234567890abcdef");

        assertThat(message.role()).isEqualTo(SessionMessageRole.USER);
        assertThat(message.content()).isEqualTo("run tests");
        assertThat(messages.saved).singleElement().isEqualTo(message);
    }

    @Test
    void appendMessageWithUserRecordsSender() {
        FakeMessageRepository messages = new FakeMessageRepository();
        SessionApplicationService service = service(new FakeWorkspaceRepository(true), new FakeSessionRepository(session()), messages);

        SessionMessage message = service.appendMessage(
                new UserId("usr_1234567890abcdef"),
                SESSION_ID,
                null,
                "run tests",
                "trace_1234567890abcdef");

        assertThat(message.senderUserId()).isEqualTo(new UserId("usr_1234567890abcdef"));
        assertThat(messages.saved).singleElement().satisfies(saved ->
                assertThat(saved.senderUserId()).isEqualTo(new UserId("usr_1234567890abcdef")));
    }

    private static SessionApplicationService service(
            WorkspaceRepository workspaces,
            SessionRepository sessions,
            SessionMessageRepository messages) {
        return new SessionApplicationService(workspaces, sessions, messages);
    }

    private static Workspace workspace() {
        return new Workspace(
                WORKSPACE_ID,
                "Demo",
                "/tmp/demo",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static Session session() {
        return new Session(
                SESSION_ID,
                WORKSPACE_ID,
                "Demo session",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private record FakeWorkspaceRepository(boolean exists) implements WorkspaceRepository {
        @Override
        public Workspace save(Workspace workspace) {
            return workspace;
        }

        @Override
        public Optional<Workspace> findById(WorkspaceId workspaceId) {
            return exists ? Optional.of(workspace()) : Optional.empty();
        }

        @Override
        public PageResponse<Workspace> findPage(PageRequest pageRequest) {
            List<Workspace> items = exists ? List.of(workspace()) : List.of();
            return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), items.size());
        }
    }

    private static final class FakeSessionRepository implements SessionRepository {
        private Session session;
        private final List<Session> saved = new ArrayList<>();

        private FakeSessionRepository(Session session) {
            this.session = session;
        }

        @Override
        public Session save(Session session) {
            this.session = session;
            saved.add(session);
            return session;
        }

        @Override
        public Optional<Session> findById(SessionId sessionId) {
            return Optional.ofNullable(session);
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
            session = session.attachOpencodeSession(opencodeSessionId, executionNodeId, updatedAt, traceId);
            return Optional.of(session);
        }
    }

    private static final class FakeMessageRepository implements SessionMessageRepository {
        private final List<SessionMessage> saved = new ArrayList<>();

        @Override
        public SessionMessage save(SessionMessage message) {
            saved.add(message);
            return message;
        }

        @Override
        public Optional<SessionMessage> findById(SessionMessageId messageId) {
            return saved.stream()
                    .filter(message -> message.messageId().equals(messageId))
                    .findFirst();
        }

        @Override
        public PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest) {
            return new PageResponse<>(saved, pageRequest.page(), pageRequest.size(), saved.size());
        }
    }
}
