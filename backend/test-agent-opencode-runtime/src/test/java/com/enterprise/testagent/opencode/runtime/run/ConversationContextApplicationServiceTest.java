package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextIssueLease;
import com.enterprise.testagent.domain.run.ConversationRunContext;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.domain.workspace.TrustedWorkspaceResolver;
import com.enterprise.testagent.domain.workspace.TrustedWorkspaceResolution;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ConversationContextApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");
    private static final String TOKEN = "ctx_fixed-token";
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final SessionId SESSION_ID = new SessionId("ses_1234567890abcdef");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_1234567890abcdef");
    private static final ConversationContextIssueLease ISSUE_LEASE = new ConversationContextIssueLease(
            USER_ID,
            SESSION_ID,
            7,
            3,
            5);

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
    private final UserOpencodeProcessAssignmentService assignmentService = mock(UserOpencodeProcessAssignmentService.class);
    private final AgentSessionBindingRepository bindingRepository = mock(AgentSessionBindingRepository.class);
    private final ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer =
            mock(ConversationWorkspaceAccessAuthorizer.class);
    private final ConversationContextStore contextStore = mock(ConversationContextStore.class);
    private ConversationContextApplicationService service;

    @BeforeEach
    void setUp() {
        when(contextStore.beginIssue(USER_ID, SESSION_ID)).thenReturn(ISSUE_LEASE);
        when(contextStore.saveIfCurrent(eq(TOKEN), any(), eq(ISSUE_LEASE))).thenReturn(true);
        service = new ConversationContextApplicationService(
                sessionRepository,
                workspaceRepository,
                assignmentService,
                bindingRepository,
                ManagedWorkspacePathResolver.legacyOnly(),
                workspaceAccessAuthorizer,
                contextStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> TOKEN);
    }

    @Test
    void bootstrapReadsAuthoritativeBindingsAndIssuesTrustedContext() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(assignmentService.requireReadyProcess(USER_ID, "opencode", "trace_test"))
                .thenReturn(new UserOpencodeProcessAssignment(node(), "server-a", process()));
        when(bindingRepository.findBySessionIdAndAgentId(SESSION_ID, "opencode"))
                .thenReturn(Optional.of(binding()));

        ConversationContextApplicationService.IssuedConversationContext issued =
                service.bootstrap(USER_ID, "OpenCode", SESSION_ID, "trace_test");

        assertThat(issued.contextToken()).isEqualTo(TOKEN);
        assertThat(issued.context().userId()).isEqualTo(USER_ID);
        assertThat(issued.context().trustedWorkspaceRoot()).isEqualTo("/srv/workspaces/demo");
        assertThat(issued.context().processId()).isEqualTo("ocp_1234567890abcdef");
        assertThat(issued.context().processSnapshot()).isEqualTo(process());
        assertThat(issued.context().remoteSessionId()).isEqualTo("remote-session-1");
        assertThat(issued.context().expiresAt()).isEqualTo(NOW.plusSeconds(24 * 60 * 60));
        verify(contextStore).saveIfCurrent(TOKEN, issued.context(), ISSUE_LEASE);
        verify(workspaceAccessAuthorizer).requireAccess(USER_ID, WORKSPACE_ID);
        InOrder issuanceOrder = inOrder(contextStore, sessionRepository);
        issuanceOrder.verify(contextStore).beginIssue(USER_ID, SESSION_ID);
        issuanceOrder.verify(sessionRepository).findById(SESSION_ID);
    }

    @Test
    void bootstrapUsesTrustedResolverSoHistoricalWorkspaceWithoutServerCanBeBoundAndSigned() {
        TrustedWorkspaceResolver trustedWorkspaceResolver = mock(TrustedWorkspaceResolver.class);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(trustedWorkspaceResolver.resolveTrustedWorkspace(WORKSPACE_ID, "trace_test"))
                .thenReturn(new TrustedWorkspaceResolution(workspace(), false));
        when(assignmentService.requireReadyProcess(USER_ID, "opencode", "trace_test"))
                .thenReturn(new UserOpencodeProcessAssignment(node(), "server-a", process()));
        when(bindingRepository.findBySessionIdAndAgentId(SESSION_ID, "opencode"))
                .thenReturn(Optional.empty());
        ConversationContextApplicationService trustedService = new ConversationContextApplicationService(
                sessionRepository,
                assignmentService,
                bindingRepository,
                trustedWorkspaceResolver,
                workspaceAccessAuthorizer,
                contextStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> TOKEN);

        var issued = trustedService.bootstrap(USER_ID, "opencode", SESSION_ID, "trace_test");

        assertThat(issued.context().workspaceSnapshot().linuxServerId()).isEqualTo("server-a");
        verify(trustedWorkspaceResolver).resolveTrustedWorkspace(WORKSPACE_ID, "trace_test");
        verify(workspaceRepository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void bootstrapRetriesOneFullAuthoritativeReadWhenHistoricalWorkspaceRebindInvalidatesLease() {
        TrustedWorkspaceResolver trustedWorkspaceResolver = mock(TrustedWorkspaceResolver.class);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(trustedWorkspaceResolver.resolveTrustedWorkspace(WORKSPACE_ID, "trace_test"))
                .thenReturn(
                        new TrustedWorkspaceResolution(workspace(), true),
                        new TrustedWorkspaceResolution(workspace(), false));
        when(assignmentService.requireReadyProcess(USER_ID, "opencode", "trace_test"))
                .thenReturn(new UserOpencodeProcessAssignment(node(), "server-a", process()));
        when(bindingRepository.findBySessionIdAndAgentId(SESSION_ID, "opencode"))
                .thenReturn(Optional.empty());
        ConversationContextApplicationService trustedService = new ConversationContextApplicationService(
                sessionRepository,
                assignmentService,
                bindingRepository,
                trustedWorkspaceResolver,
                workspaceAccessAuthorizer,
                contextStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> TOKEN);

        var issued = trustedService.bootstrap(USER_ID, "opencode", SESSION_ID, "trace_test");

        assertThat(issued.contextToken()).isEqualTo(TOKEN);
        verify(contextStore, org.mockito.Mockito.times(2)).beginIssue(USER_ID, SESSION_ID);
        verify(sessionRepository, org.mockito.Mockito.times(2)).findById(SESSION_ID);
        verify(trustedWorkspaceResolver, org.mockito.Mockito.times(2))
                .resolveTrustedWorkspace(WORKSPACE_ID, "trace_test");
        verify(contextStore).saveIfCurrent(eq(TOKEN), any(), eq(ISSUE_LEASE));
        verify(workspaceAccessAuthorizer, org.mockito.Mockito.times(2)).requireAccess(USER_ID, WORKSPACE_ID);
    }

    @Test
    void bootstrapRejectsUserWhoIsNoLongerAnActiveApplicationMember() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        org.mockito.Mockito.doThrow(new PlatformException(ErrorCode.FORBIDDEN, "用户已被移出应用"))
                .when(workspaceAccessAuthorizer)
                .requireAccess(USER_ID, WORKSPACE_ID);

        assertCode(
                () -> service.bootstrap(USER_ID, "opencode", SESSION_ID, "trace_test"),
                ErrorCode.FORBIDDEN);

        verify(contextStore).beginIssue(USER_ID, SESSION_ID);
        verify(contextStore, org.mockito.Mockito.never()).saveIfCurrent(any(), any(), any());
        verify(workspaceRepository, org.mockito.Mockito.never()).findById(any());
        verify(assignmentService, org.mockito.Mockito.never()).requireReadyProcess(any(), any(), any());
    }

    @Test
    void bootstrapRejectsLateSaveWhenAnInvalidationAdvancedTheIssuanceFence() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(assignmentService.requireReadyProcess(USER_ID, "opencode", "trace_test"))
                .thenReturn(new UserOpencodeProcessAssignment(node(), "server-a", process()));
        when(bindingRepository.findBySessionIdAndAgentId(SESSION_ID, "opencode"))
                .thenReturn(Optional.of(binding()));
        when(contextStore.saveIfCurrent(eq(TOKEN), any(), eq(ISSUE_LEASE))).thenReturn(false);

        assertCode(
                () -> service.bootstrap(USER_ID, "opencode", SESSION_ID, "trace_test"),
                ErrorCode.CONVERSATION_CONTEXT_EXPIRED);
        verify(contextStore).beginIssue(USER_ID, SESSION_ID);
        verify(sessionRepository).findById(SESSION_ID);
    }

    @Test
    void bootstrapDoesNotReuseRemoteSessionFromDifferentExecutionNode() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
        when(assignmentService.requireReadyProcess(USER_ID, "opencode", "trace_test"))
                .thenReturn(new UserOpencodeProcessAssignment(node(), "server-a", process()));
        AgentSessionBinding stale = new AgentSessionBinding(
                SESSION_ID,
                "opencode",
                "old-remote-session",
                new ExecutionNodeId("node_ocp_abcdef1234567890"),
                NOW,
                NOW,
                "trace_test");
        when(bindingRepository.findBySessionIdAndAgentId(SESSION_ID, "opencode"))
                .thenReturn(Optional.of(stale));

        assertThat(service.bootstrap(USER_ID, "opencode", SESSION_ID, "trace_test")
                        .context().remoteSessionId())
                .isNull();
    }

    @Test
    void bootstrapRejectsWorkspaceAndProcessOnDifferentLinuxServers() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        Workspace differentServer = new Workspace(
                WORKSPACE_ID,
                "demo",
                "/srv/workspaces/demo",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "server-b",
                "trace_test");
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(differentServer));
        when(assignmentService.requireReadyProcess(USER_ID, "opencode", "trace_test"))
                .thenReturn(new UserOpencodeProcessAssignment(node(), "server-a", process()));

        assertCode(
                () -> service.bootstrap(USER_ID, "opencode", SESSION_ID, "trace_test"),
                ErrorCode.CONFLICT);
    }

    @Test
    void requireRejectsMissingUnknownAndMismatchedTokens() {
        assertCode(
                () -> service.require(null, USER_ID, "opencode", SESSION_ID),
                ErrorCode.CONVERSATION_CONTEXT_REQUIRED);
        when(contextStore.peek("unknown")).thenReturn(Optional.empty());
        assertCode(
                () -> service.require("unknown", USER_ID, "opencode", SESSION_ID),
                ErrorCode.CONVERSATION_CONTEXT_EXPIRED);
        ConversationRunContext context = context();
        when(contextStore.peek(TOKEN)).thenReturn(Optional.of(context));
        when(contextStore.touch(TOKEN, context)).thenReturn(Optional.of(context));
        assertCode(
                () -> service.require(TOKEN, new UserId("usr_abcdef1234567890"), "opencode", SESSION_ID),
                ErrorCode.CONVERSATION_CONTEXT_EXPIRED);
        assertCode(
                () -> service.require(TOKEN, USER_ID, "other", SESSION_ID),
                ErrorCode.CONVERSATION_CONTEXT_EXPIRED);
    }

    @Test
    void requireValidatesPeekBeforeAtomicallyTouchingContext() {
        ConversationRunContext context = context();
        ConversationRunContext refreshed = context.withExpiresAt(NOW.plusSeconds(24 * 60 * 60));
        when(contextStore.peek(TOKEN)).thenReturn(Optional.of(context));
        when(contextStore.touch(TOKEN, context)).thenReturn(Optional.of(refreshed));

        assertThat(service.require(TOKEN, USER_ID, "opencode", SESSION_ID)).isEqualTo(refreshed);

        verify(contextStore).peek(TOKEN);
        verify(contextStore).touch(TOKEN, context);
    }

    @Test
    void invalidateDelegatesToReverseIndexStore() {
        service.invalidate(USER_ID, SESSION_ID);

        verify(contextStore).invalidate(USER_ID, SESSION_ID);
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }

    private static Session session() {
        return new Session(
                SESSION_ID,
                WORKSPACE_ID,
                "session",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_test",
                null,
                null,
                false,
                ConversationSourceType.MANUAL,
                null,
                USER_ID);
    }

    private static Workspace workspace() {
        return new Workspace(
                WORKSPACE_ID,
                "demo",
                "/srv/workspaces/demo",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "server-a",
                "trace_test");
    }

    private static ExecutionNode node() {
        return new ExecutionNode(
                new ExecutionNodeId("node_ocp_1234567890abcdef"),
                "http://10.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                NOW,
                Set.of("opencode"),
                NOW,
                NOW,
                "trace_test");
    }

    private static AgentSessionBinding binding() {
        return new AgentSessionBinding(
                SESSION_ID,
                "opencode",
                "remote-session-1",
                node().executionNodeId(),
                NOW,
                NOW,
                "trace_test");
    }

    private static OpencodeServerProcess process() {
        return new OpencodeServerProcess(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                USER_ID,
                new LinuxServerId("server-a"),
                new OpencodeContainerId("ctr_1234567890abcdef"),
                4096,
                12345L,
                "http://10.0.0.1:4096",
                OpencodeServerProcessStatus.RUNNING,
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode",
                NOW.minusSeconds(60),
                NOW,
                "ok",
                NOW.minusSeconds(60),
                NOW,
                "trace_test");
    }

    private static ConversationRunContext context() {
        AgentSessionBinding binding = binding();
        return new ConversationRunContext(
                USER_ID,
                "opencode",
                "ocp_1234567890abcdef",
                "server-a",
                session(),
                workspace(),
                node(),
                binding,
                1,
                NOW.plusSeconds(3600));
    }
}
