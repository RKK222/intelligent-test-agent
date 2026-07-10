package com.icbc.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.configuration.ApplicationDefinition;
import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspace;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedConversationWorkspaceAccessAuthorizerTest {

    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_1234567890abcdef");
    private static final ApplicationId APP_ID = new ApplicationId("app_1234567890abcdef");
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    private final ManagedWorkspaceRepository managedRepository = mock(ManagedWorkspaceRepository.class);
    private final ConfigurationManagementRepository configurationRepository =
            mock(ConfigurationManagementRepository.class);
    private final ManagedConversationWorkspaceAccessAuthorizer authorizer =
            new ManagedConversationWorkspaceAccessAuthorizer(managedRepository, configurationRepository);

    @Test
    void activeMemberCanUseManagedApplicationWorkspace() {
        ApplicationWorkspaceVersion version = mock(ApplicationWorkspaceVersion.class);
        when(version.appId()).thenReturn(APP_ID);
        when(managedRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.of(version));
        when(configurationRepository.findApplication(APP_ID)).thenReturn(Optional.of(application(true)));
        when(configurationRepository.isActiveMember(APP_ID, USER_ID)).thenReturn(true);

        authorizer.requireAccess(USER_ID, WORKSPACE_ID);

        verify(configurationRepository).isActiveMember(APP_ID, USER_ID);
        verify(managedRepository, never()).findPersonalWorkspaceByRuntimeWorkspace(WORKSPACE_ID);
    }

    @Test
    void removedMemberCannotReissueContextForPersonalWorkspace() {
        PersonalWorkspace personal = mock(PersonalWorkspace.class);
        when(personal.appId()).thenReturn(APP_ID);
        when(personal.userId()).thenReturn(USER_ID);
        when(managedRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(managedRepository.findPersonalWorkspaceByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.of(personal));
        when(configurationRepository.findApplication(APP_ID)).thenReturn(Optional.of(application(true)));
        when(configurationRepository.isActiveMember(APP_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> authorizer.requireAccess(USER_ID, WORKSPACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void anotherApplicationMemberCannotUseSomeoneElsesPersonalWorkspace() {
        PersonalWorkspace personal = mock(PersonalWorkspace.class);
        when(personal.userId()).thenReturn(new UserId("usr_abcdef1234567890"));
        when(managedRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(managedRepository.findPersonalWorkspaceByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.of(personal));

        assertThatThrownBy(() -> authorizer.requireAccess(USER_ID, WORKSPACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN));
        verify(configurationRepository, never()).isActiveMember(APP_ID, USER_ID);
    }

    @Test
    void disabledApplicationCannotIssueContext() {
        ApplicationWorkspaceVersion version = mock(ApplicationWorkspaceVersion.class);
        when(version.appId()).thenReturn(APP_ID);
        when(managedRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.of(version));
        when(configurationRepository.findApplication(APP_ID)).thenReturn(Optional.of(application(false)));

        assertThatThrownBy(() -> authorizer.requireAccess(USER_ID, WORKSPACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN));
        verify(configurationRepository, never()).isActiveMember(APP_ID, USER_ID);
    }

    @Test
    void unmanagedHistoricalWorkspaceKeepsSessionOwnerPolicy() {
        when(managedRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(managedRepository.findPersonalWorkspaceByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());

        authorizer.requireAccess(USER_ID, WORKSPACE_ID);

        verify(configurationRepository, never()).findApplication(APP_ID);
        verify(configurationRepository, never()).isActiveMember(APP_ID, USER_ID);
    }

    private static ApplicationDefinition application(boolean enabled) {
        return new ApplicationDefinition(APP_ID, "Demo", enabled, NOW, NOW);
    }
}
