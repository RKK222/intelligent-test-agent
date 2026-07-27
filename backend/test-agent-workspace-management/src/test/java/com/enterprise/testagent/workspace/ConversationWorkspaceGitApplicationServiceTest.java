package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationWorkspaceGitApplicationServiceTest {

    private static final String REMOTE_SESSION_ID = "ses_remote_123";
    private static final String TRACE_ID = "trace_1234567890abcdef";
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_1234567890abcdef");
    private static final PersonalWorkspaceId PERSONAL_ID = new PersonalWorkspaceId("pws_1234567890abcdef");

    private AgentSessionBindingRepository bindingRepository;
    private SessionRepository sessionRepository;
    private ManagedWorkspaceRepository managedWorkspaceRepository;
    private ManagedWorkspaceApplicationService workspaceService;
    private ConversationWorkspaceGitApplicationService service;

    @BeforeEach
    void setUp() {
        bindingRepository = mock(AgentSessionBindingRepository.class);
        sessionRepository = mock(SessionRepository.class);
        managedWorkspaceRepository = mock(ManagedWorkspaceRepository.class);
        workspaceService = mock(ManagedWorkspaceApplicationService.class);
        service = new ConversationWorkspaceGitApplicationService(
                bindingRepository, sessionRepository, managedWorkspaceRepository, workspaceService);

        AgentSessionBinding binding = mock(AgentSessionBinding.class);
        SessionId sessionId = new SessionId("ses_1234567890abcdef");
        when(binding.sessionId()).thenReturn(sessionId);
        Session session = mock(Session.class);
        when(session.workspaceId()).thenReturn(WORKSPACE_ID);
        PersonalWorkspace personal = mock(PersonalWorkspace.class);
        when(personal.userId()).thenReturn(USER_ID);
        when(personal.runtimeWorkspaceId()).thenReturn(WORKSPACE_ID);
        when(personal.personalWorkspaceId()).thenReturn(PERSONAL_ID);
        when(bindingRepository.findByAgentIdAndRemoteSessionId("opencode", REMOTE_SESSION_ID))
                .thenReturn(Optional.of(binding));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(managedWorkspaceRepository.findPersonalWorkspaceByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.of(personal));
    }

    @Test
    void statusUsesWorkspaceBoundToCurrentRemoteSession() {
        var diff = new ManagedWorkspaceResponses.WorkspaceGitDiffResponse(List.of());
        when(workspaceService.getWorkspaceGitDiff(WORKSPACE_ID.value(), USER_ID)).thenReturn(diff);

        var result = service.execute(
                REMOTE_SESSION_ID, "status", null, null, null, null, null, null,
                USER_ID, List.of(Dictionary.ROLE_USER), TRACE_ID);

        assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID.value());
        assertThat(result.personalWorkspaceId()).isEqualTo(PERSONAL_ID.value());
        assertThat(result.result()).isSameAs(diff);
    }

    @Test
    void protectedFileIsRejectedBeforeGitMutationForNormalUser() {
        assertThatThrownBy(() -> service.execute(
                REMOTE_SESSION_ID,
                "stage",
                List.of(".opencode/agents/review.md"),
                null,
                null,
                null,
                null,
                null,
                USER_ID,
                List.of(Dictionary.ROLE_USER),
                TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(workspaceService, never()).stageWorkspaceGitFiles(
                WORKSPACE_ID.value(), List.of(".opencode/agents/review.md"), USER_ID);
    }

    @Test
    void pullOnlyTargetsCurrentOwnersPersonalWorkspace() {
        service.execute(
                REMOTE_SESSION_ID, "pull", null, null, null, null, null, null,
                USER_ID, List.of(Dictionary.ROLE_USER), TRACE_ID);

        verify(workspaceService).gitPullPersonalWorkspace(PERSONAL_ID.value(), USER_ID, TRACE_ID);
    }
}
