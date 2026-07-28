package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AppSourceWorkspaceAccessTest {

    private static final Instant NOW = Instant.parse("2026-07-28T06:00:00Z");
    private static final ApplicationId APP_ID = new ApplicationId("app_1");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_source");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_source_g1");
    private static final UserId USER_ID = new UserId("usr_member");

    @Test
    void revokedMemberCannotUseTeamAppSourceWorkspaceEvenThoughWorkspaceLooksUnmanaged() {
        ManagedWorkspaceRepository managed = mock(ManagedWorkspaceRepository.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        when(managed.findVersionByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(managed.findVersionReplicaByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(managed.findPersonalWorkspaceByRuntimeWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());
        when(appSources.findReplicaByRuntimeWorkspaceId(WORKSPACE_ID.value())).thenReturn(Optional.of(replica()));
        when(appSources.findSnapshot(REPOSITORY_ID, 1L)).thenReturn(Optional.of(snapshot(AppSourcePurpose.TEAM)));
        when(configuration.findApplicationsByRepository(REPOSITORY_ID)).thenReturn(List.of(application()));
        when(configuration.findRepositoriesByApplication(APP_ID)).thenReturn(List.of(repository()));
        when(configuration.findApplication(APP_ID)).thenReturn(Optional.of(application()));
        when(configuration.isActiveMember(APP_ID, USER_ID)).thenReturn(false);
        ManagedConversationWorkspaceAccessAuthorizer authorizer =
                new ManagedConversationWorkspaceAccessAuthorizer(
                        managed, configuration, appSources, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> authorizer.requireAccess(USER_ID, WORKSPACE_ID))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void activeMemberCanWriteTheReadyTeamReplicaOnTheSameServer() {
        ManagedWorkspaceRepository managed = mock(ManagedWorkspaceRepository.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        when(appSources.findReplicaByRuntimeWorkspaceId(WORKSPACE_ID.value())).thenReturn(Optional.of(replica()));
        when(appSources.findSnapshot(REPOSITORY_ID, 1L)).thenReturn(Optional.of(snapshot(AppSourcePurpose.TEAM)));
        when(configuration.findApplicationsByRepository(REPOSITORY_ID)).thenReturn(List.of(application()));
        when(configuration.findRepositoriesByApplication(APP_ID)).thenReturn(List.of(repository()));
        when(configuration.isActiveMember(APP_ID, USER_ID)).thenReturn(true);
        ManagedConversationWorkspaceAccessAuthorizer authorizer =
                new ManagedConversationWorkspaceAccessAuthorizer(
                        managed, configuration, appSources, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatCode(() -> authorizer.requireAccess(USER_ID, WORKSPACE_ID)).doesNotThrowAnyException();
    }

    @Test
    void personalReplicaRejectsAnActiveMemberWhoIsNotTheOwner() {
        ManagedWorkspaceRepository managed = mock(ManagedWorkspaceRepository.class);
        ConfigurationManagementRepository configuration = mock(ConfigurationManagementRepository.class);
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        when(appSources.findReplicaByRuntimeWorkspaceId(WORKSPACE_ID.value())).thenReturn(Optional.of(replica()));
        when(appSources.findSnapshot(REPOSITORY_ID, 1L)).thenReturn(Optional.of(snapshot(AppSourcePurpose.PERSONAL)));
        ManagedConversationWorkspaceAccessAuthorizer authorizer =
                new ManagedConversationWorkspaceAccessAuthorizer(
                        managed, configuration, appSources, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> authorizer.requireAccess(USER_ID, WORKSPACE_ID))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private AppSourceReplica replica() {
        return new AppSourceReplica(
                REPOSITORY_ID, 1L, new LinuxServerId("server-a"), WORKSPACE_ID, AppSourceReplicaStatus.READY,
                null, null, 1, null, null, null, NOW, NOW);
    }

    private AppSourceSnapshot snapshot(AppSourcePurpose purpose) {
        return new AppSourceSnapshot(
                REPOSITORY_ID, 1L, "billing-service", purpose, new UserId("usr_downloader"),
                "main", "a".repeat(40), List.of(new AppSourceSelectedPath(".", AppSourcePathType.DIRECTORY)),
                "b".repeat(64), NOW, NOW.plusSeconds(3600), AppSourceSnapshotStatus.ACTIVE,
                NOW, NOW);
    }

    private ApplicationDefinition application() {
        return new ApplicationDefinition(APP_ID, "Billing", true, NOW.minusSeconds(100), NOW);
    }

    private CodeRepository repository() {
        return new CodeRepository(
                REPOSITORY_ID, "/git/repo.git", "Billing", "billing-service",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(), false, NOW.minusSeconds(100), NOW);
    }
}
