package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer.FileWorkspaceKind;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import java.time.Clock;
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
    void historicalAutomationWorkspaceUsesReadonlyFileClassification() {
        ApplicationWorkspaceVersion version = mock(ApplicationWorkspaceVersion.class);
        CodeRepositoryId repositoryId = new CodeRepositoryId("repo_automation");
        when(version.appId()).thenReturn(APP_ID);
        when(version.repositoryId()).thenReturn(repositoryId);
        when(managedRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.of(version));
        when(configurationRepository.findRepository(repositoryId)).thenReturn(Optional.of(new CodeRepository(
                repositoryId,
                "https://git.example.test/automation.git",
                "自动化仓库",
                "automation",
                CodeRepositoryType.AUTOMATION_CODE_REPOSITORY.value(),
                false,
                NOW,
                NOW)));
        when(configurationRepository.findApplication(APP_ID)).thenReturn(Optional.of(application(true)));
        when(configurationRepository.isActiveMember(APP_ID, USER_ID)).thenReturn(true);

        org.assertj.core.api.Assertions.assertThat(authorizer.requireClassifiedFileAccess(USER_ID, WORKSPACE_ID, false))
                .isEqualTo(FileWorkspaceKind.AUTOMATION_REFERENCE);
    }

    @Test
    void removedMemberCannotUseApplicationVersionReplicaWorkspace() {
        ApplicationWorkspaceVersionReplica replica = mock(ApplicationWorkspaceVersionReplica.class);
        ApplicationWorkspaceVersion version = mock(ApplicationWorkspaceVersion.class);
        ApplicationWorkspaceVersionId versionId = new ApplicationWorkspaceVersionId("awv_1234567890abcdef");
        when(replica.versionId()).thenReturn(versionId);
        when(version.appId()).thenReturn(APP_ID);
        when(managedRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(managedRepository.findVersionReplicaByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.of(replica));
        when(managedRepository.findVersion(versionId)).thenReturn(Optional.of(version));
        when(configurationRepository.findApplication(APP_ID)).thenReturn(Optional.of(application(true)));
        when(configurationRepository.isActiveMember(APP_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> authorizer.requireAccess(USER_ID, WORKSPACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void applicationVersionReplicaWithoutVersionMappingFailsClosed() {
        ApplicationWorkspaceVersionReplica replica = mock(ApplicationWorkspaceVersionReplica.class);
        ApplicationWorkspaceVersionId versionId = new ApplicationWorkspaceVersionId("awv_missing_mapping");
        when(replica.versionId()).thenReturn(versionId);
        when(managedRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(managedRepository.findVersionReplicaByRuntimeWorkspace(WORKSPACE_ID))
                .thenReturn(Optional.of(replica));
        when(managedRepository.findVersion(versionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizer.requireAccess(USER_ID, WORKSPACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN));
        verify(configurationRepository, never()).isActiveMember(APP_ID, USER_ID);
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

    @Test
    void unmanagedWorkspaceFileAccessRequiresSuperAdminCompatibilityFlag() {
        when(managedRepository.findVersionByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(managedRepository.findVersionReplicaByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(managedRepository.findPersonalWorkspaceByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizer.requireFileAccess(USER_ID, WORKSPACE_ID, false))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN));

        authorizer.requireFileAccess(USER_ID, WORKSPACE_ID, true);
        verify(configurationRepository, never()).findApplication(APP_ID);
        verify(configurationRepository, never()).isActiveMember(APP_ID, USER_ID);
    }

    @Test
    void experienceWorkspaceAlwaysUsesRealtimeExperiencePolicyAndNeverFallsBackToUnmanaged() {
        WorkspaceId experienceId = new WorkspaceId("wrk_exp_1234567890abcdef");
        ExperienceWorkspaceAccessAuthorizer experienceAuthorizer = mock(ExperienceWorkspaceAccessAuthorizer.class);
        when(experienceAuthorizer.isExperienceWorkspace(experienceId)).thenReturn(true);
        when(experienceAuthorizer.requireAccess(USER_ID, experienceId)).thenReturn(mock(Workspace.class));
        ManagedConversationWorkspaceAccessAuthorizer experienceAware =
                new ManagedConversationWorkspaceAccessAuthorizer(
                        managedRepository,
                        configurationRepository,
                        null,
                        mock(WorkspaceRepository.class),
                        experienceAuthorizer,
                        Clock.systemUTC());

        FileWorkspaceKind kind = experienceAware.requireClassifiedFileAccess(
                USER_ID, experienceId, false);

        org.assertj.core.api.Assertions.assertThat(kind).isEqualTo(FileWorkspaceKind.EXPERIENCE);
        verify(experienceAuthorizer).requireAccess(USER_ID, experienceId);
        verify(managedRepository, never()).findVersionByRuntimeWorkspace(experienceId);
    }

    private static ApplicationDefinition application(boolean enabled) {
        return new ApplicationDefinition(APP_ID, "Demo", enabled, NOW, NOW);
    }
}
