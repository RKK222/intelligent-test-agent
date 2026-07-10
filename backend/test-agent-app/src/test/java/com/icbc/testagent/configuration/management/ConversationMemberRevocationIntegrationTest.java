package com.icbc.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.git.SshKeyEncryptionService;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.configuration.ApplicationDefinition;
import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.dictionary.DictionaryRepository;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.run.ConversationContextIssueLease;
import com.icbc.testagent.domain.run.ConversationContextStore;
import com.icbc.testagent.domain.run.ConversationContextUserMutation;
import com.icbc.testagent.domain.run.ConversationRunContext;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import com.icbc.testagent.domain.workspace.TrustedWorkspaceResolution;
import com.icbc.testagent.domain.workspace.TrustedWorkspaceResolver;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.opencode.runtime.run.ConversationContextApplicationService;
import com.icbc.testagent.workspace.ManagedConversationWorkspaceAccessAuthorizer;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 跨模块验证应用成员撤权同时阻断旧 token 和后续重新签发。
 */
class ConversationMemberRevocationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final SessionId SESSION_ID = new SessionId("ses_1234567890abcdef");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_1234567890abcdef");
    private static final ApplicationId APP_ID = new ApplicationId("app_1234567890abcdef");

    @Test
    void removingMemberInvalidatesOldTokenAndMakesNewBootstrapForbidden() {
        AtomicBoolean activeMember = new AtomicBoolean(true);
        ConfigurationManagementRepository configurationRepository = mock(ConfigurationManagementRepository.class);
        when(configurationRepository.findApplication(APP_ID))
                .thenReturn(Optional.of(new ApplicationDefinition(APP_ID, "Demo", true, NOW, NOW)));
        when(configurationRepository.isActiveMember(APP_ID, USER_ID))
                .thenAnswer(ignored -> activeMember.get());
        org.mockito.Mockito.doAnswer(ignored -> {
                    activeMember.set(false);
                    return null;
                })
                .when(configurationRepository)
                .deleteMember(APP_ID, USER_ID);

        ManagedWorkspaceRepository managedWorkspaceRepository = mock(ManagedWorkspaceRepository.class);
        ApplicationWorkspaceVersion version = mock(ApplicationWorkspaceVersion.class);
        when(version.appId()).thenReturn(APP_ID);
        when(managedWorkspaceRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.of(version));
        ManagedConversationWorkspaceAccessAuthorizer authorizer =
                new ManagedConversationWorkspaceAccessAuthorizer(
                        managedWorkspaceRepository,
                        configurationRepository);

        ConversationContextStore contextStore = mock(ConversationContextStore.class);
        ConversationContextIssueLease issueLease =
                new ConversationContextIssueLease(USER_ID, SESSION_ID, 0, 0, 0);
        ConversationContextUserMutation mutation =
                new ConversationContextUserMutation(USER_ID, "mutation-member-removal");
        AtomicBoolean tokenValid = new AtomicBoolean();
        AtomicReference<ConversationRunContext> storedContext = new AtomicReference<>();
        when(contextStore.beginIssue(USER_ID, SESSION_ID)).thenReturn(issueLease);
        when(contextStore.saveIfCurrent(anyString(), any(), any())).thenAnswer(invocation -> {
            storedContext.set(invocation.getArgument(1));
            tokenValid.set(true);
            return true;
        });
        when(contextStore.peek(anyString())).thenAnswer(ignored -> tokenValid.get()
                ? Optional.of(storedContext.get())
                : Optional.empty());
        when(contextStore.beginUserMutation(USER_ID)).thenReturn(mutation);
        org.mockito.Mockito.doAnswer(ignored -> {
                    tokenValid.set(false);
                    return null;
                })
                .when(contextStore)
                .completeUserMutation(mutation);

        SessionRepository sessionRepository = mock(SessionRepository.class);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        TrustedWorkspaceResolver trustedWorkspaceResolver = mock(TrustedWorkspaceResolver.class);
        when(trustedWorkspaceResolver.resolveTrustedWorkspace(WORKSPACE_ID, "trace_test"))
                .thenReturn(new TrustedWorkspaceResolution(workspace(), false));
        UserOpencodeProcessAssignmentService assignmentService = mock(UserOpencodeProcessAssignmentService.class);
        when(assignmentService.requireReadyProcess(USER_ID, "opencode", "trace_test"))
                .thenReturn(new UserOpencodeProcessAssignment(node(), "server-a", process()));
        AgentSessionBindingRepository bindingRepository = mock(AgentSessionBindingRepository.class);
        when(bindingRepository.findBySessionIdAndAgentId(SESSION_ID, "opencode"))
                .thenReturn(Optional.empty());
        ConversationContextApplicationService contextService = new ConversationContextApplicationService(
                sessionRepository,
                assignmentService,
                bindingRepository,
                trustedWorkspaceResolver,
                authorizer,
                contextStore);

        var issued = contextService.bootstrap(USER_ID, "opencode", SESSION_ID, "trace_test");
        assertThat(issued.contextToken()).isNotBlank();

        ConfigurationManagementApplicationService configurationService =
                new ConfigurationManagementApplicationService(
                        configurationRepository,
                        mock(DictionaryRepository.class),
                        mock(UserRepository.class),
                        mock(GitCloneCacheService.class),
                        mock(SshKeyEncryptionService.class),
                        managedWorkspaceRepository);
        configurationService.setConversationContextStore(contextStore);
        configurationService.removeMember(APP_ID.value(), USER_ID.value());

        assertThatThrownBy(() -> contextService.require(
                        issued.contextToken(), USER_ID, "opencode", SESSION_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONVERSATION_CONTEXT_EXPIRED));
        assertThatThrownBy(() -> contextService.bootstrap(USER_ID, "opencode", SESSION_ID, "trace_test"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(contextStore).completeUserMutation(mutation);
        verify(assignmentService, org.mockito.Mockito.times(1))
                .requireReadyProcess(USER_ID, "opencode", "trace_test");
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
                "/data/opencode/config",
                NOW,
                NOW,
                "healthy",
                NOW,
                NOW,
                "trace_test");
    }
}
