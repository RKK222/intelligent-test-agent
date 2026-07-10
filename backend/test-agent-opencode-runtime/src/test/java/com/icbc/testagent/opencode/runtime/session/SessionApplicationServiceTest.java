package com.icbc.testagent.opencode.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.run.ConversationContextStore;
import com.icbc.testagent.domain.run.ConversationContextSessionRevocation;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionHistoryItem;
import com.icbc.testagent.domain.session.SessionHistoryRepository;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.session.SessionWorkspaceContext;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import com.icbc.testagent.opencode.runtime.run.RunSessionMessageSnapshotService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
    void archiveSessionEstablishesRevocationGateBeforePersisting() {
        UserId userId = new UserId("usr_1234567890abcdef");
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        ConversationContextSessionRevocation revocation = new ConversationContextSessionRevocation(
                SESSION_ID,
                "revoke_123");
        Mockito.when(contextStore.revokeSession(SESSION_ID)).thenReturn(revocation);
        SessionRepository sessions = Mockito.mock(SessionRepository.class);
        Mockito.when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        Mockito.when(sessions.save(Mockito.any(Session.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SessionApplicationService service = new SessionApplicationService(
                new FakeWorkspaceRepository(true),
                sessions,
                Mockito.mock(SessionHistoryRepository.class),
                new FakeMessageRepository(),
                null,
                contextStore);

        Session archived = service.archiveSession(userId, SESSION_ID, "trace_1234567890abcdef");

        assertThat(archived.status()).isEqualTo(SessionStatus.ARCHIVED);
        assertThat(session().createdByUserId()).isNull();
        org.mockito.InOrder order = Mockito.inOrder(contextStore, sessions);
        order.verify(contextStore).revokeSession(SESSION_ID);
        order.verify(sessions).save(Mockito.any(Session.class));
        verify(contextStore, never()).restoreSessionRevocation(revocation);
        verify(contextStore, never()).invalidateSession(SESSION_ID);
        verify(contextStore, never()).invalidate(userId, SESSION_ID);
    }

    @Test
    void archiveDoesNotPersistWhenRuntimeContextInvalidationFails() {
        UserId userId = new UserId("usr_1234567890abcdef");
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        Mockito.doThrow(new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE))
                .when(contextStore)
                .revokeSession(SESSION_ID);
        FakeSessionRepository sessions = new FakeSessionRepository(session());
        SessionApplicationService service = new SessionApplicationService(
                new FakeWorkspaceRepository(true),
                sessions,
                Mockito.mock(SessionHistoryRepository.class),
                new FakeMessageRepository(),
                null,
                contextStore);

        assertThatThrownBy(() -> service.archiveSession(userId, SESSION_ID, "trace_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RUNTIME_STATE_UNAVAILABLE));

        assertThat(sessions.saved).isEmpty();
        assertThat(sessions.findById(SESSION_ID)).get()
                .extracting(Session::status)
                .isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void archiveDatabaseFailureRollsBackOnlyItsRevocationToken() {
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        ConversationContextSessionRevocation revocation = new ConversationContextSessionRevocation(
                SESSION_ID,
                "revoke_own");
        Mockito.when(contextStore.revokeSession(SESSION_ID)).thenReturn(revocation);
        SessionRepository sessions = Mockito.mock(SessionRepository.class);
        Mockito.when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        Mockito.when(sessions.save(Mockito.any(Session.class)))
                .thenThrow(new IllegalStateException("db unavailable"));
        SessionApplicationService service = new SessionApplicationService(
                new FakeWorkspaceRepository(true),
                sessions,
                Mockito.mock(SessionHistoryRepository.class),
                new FakeMessageRepository(),
                null,
                contextStore);

        assertThatThrownBy(() -> service.archiveSession(SESSION_ID, "trace_1234567890abcdef"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db unavailable");

        verify(contextStore).restoreSessionRevocation(revocation);
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

    @Test
    void listMessagesCanSkipAgentSnapshotRefreshForReadOnlyMapping() {
        RunSessionMessageSnapshotService snapshotService = Mockito.mock(RunSessionMessageSnapshotService.class);
        FakeMessageRepository messages = new FakeMessageRepository();
        Session activeSession = session();
        messages.save(new SessionMessage(
                new SessionMessageId("msg_1234567890abcdef1234567890abcdef"),
                activeSession.sessionId(),
                SessionMessageRole.ASSISTANT,
                "done",
                NOW,
                "trace_1234567890abcdef"));
        SessionApplicationService service = new SessionApplicationService(
                new FakeWorkspaceRepository(true),
                new FakeSessionRepository(activeSession),
                messages,
                snapshotService);

        PageResponse<SessionMessage> page = service.listMessages(
                SESSION_ID,
                new PageRequest(1, 20),
                "trace_1234567890abcdef",
                false);

        assertThat(page.items()).hasSize(1);
        verify(snapshotService, never()).refreshSessionSnapshot(eq("opencode"), eq(activeSession), eq("trace_1234567890abcdef"));
    }

    @Test
    void listMessagesRefreshesAgentSnapshotByDefaultForCompatibility() {
        RunSessionMessageSnapshotService snapshotService = Mockito.mock(RunSessionMessageSnapshotService.class);
        Session activeSession = session();
        SessionApplicationService service = new SessionApplicationService(
                new FakeWorkspaceRepository(true),
                new FakeSessionRepository(activeSession),
                new FakeMessageRepository(),
                snapshotService);

        service.listMessages(SESSION_ID, new PageRequest(1, 20), "trace_1234567890abcdef");

        verify(snapshotService).refreshSessionSnapshot(eq("opencode"), eq(activeSession), eq("trace_1234567890abcdef"));
    }

    @Test
    void listUserSessionsUsesCurrentUserHistoryRepositoryAndKeepsRepositoryOrder() {
        UserId userId = new UserId("usr_1234567890abcdef");
        Session olderPinned = session().updateTitleAndPinned("Older pinned", true, NOW.plusSeconds(5), "trace_1234567890abcdef");
        Session newer = session().updateTitleAndPinned("Newer", false, NOW.plusSeconds(30), "trace_1234567890abcdef");
        FakeSessionHistoryRepository history = new FakeSessionHistoryRepository(new PageResponse<>(
                List.of(
                        new SessionHistoryItem(newer, new SessionWorkspaceContext(
                                "app_1234567890abcdef",
                                "智能测试",
                                "aw_1234567890abcdef",
                                "主工作区",
                                "ver_1234567890abcdef",
                                "20260708")),
                        new SessionHistoryItem(olderPinned, null)),
                1,
                30,
                2));
        SessionApplicationService service = new SessionApplicationService(
                new FakeWorkspaceRepository(true),
                new FakeSessionRepository(session()),
                history,
                new FakeMessageRepository(),
                null);

        PageResponse<SessionHistoryItem> page = service.listUserSessions(userId, "demo", new PageRequest(1, 30));

        assertThat(history.capturedUserId).isEqualTo(userId);
        assertThat(history.capturedQuery).isEqualTo("demo");
        assertThat(history.capturedPageRequest).isEqualTo(new PageRequest(1, 30));
        assertThat(page.items())
                .extracting(item -> item.session().title())
                .containsExactly("Newer", "Older pinned");
        assertThat(page.items().getFirst().workspaceContext().appName()).isEqualTo("智能测试");
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

    private static final class FakeSessionHistoryRepository implements SessionHistoryRepository {
        private final PageResponse<SessionHistoryItem> page;
        private UserId capturedUserId;
        private String capturedQuery;
        private PageRequest capturedPageRequest;

        private FakeSessionHistoryRepository(PageResponse<SessionHistoryItem> page) {
            this.page = page;
        }

        @Override
        public PageResponse<SessionHistoryItem> findUserHistory(UserId userId, String query, PageRequest pageRequest) {
            this.capturedUserId = userId;
            this.capturedQuery = query;
            this.capturedPageRequest = pageRequest;
            return page;
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
