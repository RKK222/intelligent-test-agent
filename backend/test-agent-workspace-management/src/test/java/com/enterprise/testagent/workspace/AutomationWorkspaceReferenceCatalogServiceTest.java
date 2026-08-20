package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspace;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceActiveVersion;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceActiveVersionRepository;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.enterprise.testagent.domain.managedworkspace.WorkspaceReplicaSyncStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutomationWorkspaceReferenceCatalogServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T05:00:00Z");
    private static final ApplicationId APP_ID = new ApplicationId("app_demo");
    private static final UserId USER_ID = new UserId("usr_demo");
    private static final WorkspaceId HOST_WORKSPACE_ID = new WorkspaceId("wrk_host");
    private static final CodeRepositoryId HOST_REPOSITORY_ID = new CodeRepositoryId("repo_host");

    @TempDir
    Path tempDir;

    private ConfigurationManagementRepository configurationRepository;
    private ManagedWorkspaceRepository managedWorkspaceRepository;
    private AutomationWorkspaceActiveVersionRepository activeVersionRepository;
    private WorkspaceRepository workspaceRepository;
    private ManagedWorkspacePathResolver pathResolver;
    private AutomationWorkspaceReferenceCatalogService service;

    @BeforeEach
    void setUp() {
        configurationRepository = mock(ConfigurationManagementRepository.class);
        managedWorkspaceRepository = mock(ManagedWorkspaceRepository.class);
        activeVersionRepository = mock(AutomationWorkspaceActiveVersionRepository.class);
        workspaceRepository = mock(WorkspaceRepository.class);
        pathResolver = mock(ManagedWorkspacePathResolver.class);
        service = new AutomationWorkspaceReferenceCatalogService(
                configurationRepository,
                managedWorkspaceRepository,
                activeVersionRepository,
                workspaceRepository,
                pathResolver,
                new WorkspaceServerIdentity("server-a"));

        ApplicationWorkspaceVersion hostVersion = mock(ApplicationWorkspaceVersion.class);
        when(hostVersion.appId()).thenReturn(APP_ID);
        when(hostVersion.repositoryId()).thenReturn(HOST_REPOSITORY_ID);
        when(managedWorkspaceRepository.findVersionByRuntimeWorkspace(HOST_WORKSPACE_ID))
                .thenReturn(Optional.of(hostVersion));
        when(configurationRepository.findRepository(HOST_REPOSITORY_ID))
                .thenReturn(Optional.of(repository(HOST_REPOSITORY_ID, "主工作库", CodeRepositoryType.TEST_WORK_REPOSITORY)));
        when(configurationRepository.findApplication(APP_ID))
                .thenReturn(Optional.of(new ApplicationDefinition(APP_ID, "Demo", true, NOW, NOW)));
        when(configurationRepository.isActiveMember(APP_ID, USER_ID)).thenReturn(true);
    }

    @Test
    void listsEveryReadyActiveVersionAndDisambiguatesDuplicateWorkspaceNames() {
        Fixture first = fixture("one", "自动化", "repo-one", "main", true);
        Fixture second = fixture("two", "自动化", "repo-two", "release", true);
        when(configurationRepository.findRepositoriesByApplication(APP_ID))
                .thenReturn(List.of(first.repository(), second.repository()));
        when(configurationRepository.findWorkspaces(APP_ID)).thenReturn(List.of(first.template(), second.template()));
        when(activeVersionRepository.findByApplicationWorkspaceIds(List.of(
                first.template().workspaceId(), second.template().workspaceId())))
                .thenReturn(List.of(first.active(), second.active()));

        var resolution = service.resolveActive(USER_ID, HOST_WORKSPACE_ID);

        assertThat(resolution.configuredCount()).isEqualTo(2);
        assertThat(resolution.warnings()).isEmpty();
        assertThat(resolution.references())
                .extracting(reference -> reference.displayName())
                .containsExactly("自动化（repo-one / main）", "自动化（repo-two / release）");
    }

    @Test
    void isolatesMissingActiveVersionAndUnavailableReplicaAsLocalWarnings() {
        Fixture unavailable = fixture("unready", "接口自动化", "repo-unready", "main", false);
        Fixture inactive = fixture("inactive", "UI 自动化", "repo-inactive", "main", true);
        when(configurationRepository.findRepositoriesByApplication(APP_ID))
                .thenReturn(List.of(unavailable.repository(), inactive.repository()));
        when(configurationRepository.findWorkspaces(APP_ID))
                .thenReturn(List.of(unavailable.template(), inactive.template()));
        when(activeVersionRepository.findByApplicationWorkspaceIds(anyList()))
                .thenReturn(List.of(unavailable.active()));

        var resolution = service.resolveActive(USER_ID, HOST_WORKSPACE_ID);

        assertThat(resolution.references()).isEmpty();
        assertThat(resolution.warnings())
                .extracting(warning -> warning.code())
                .containsExactlyInAnyOrder(ErrorCode.CONFLICT.name(), "NO_ACTIVE_VERSION");
    }

    @Test
    void exactPreviouslyOpenedVersionRemainsResolvableAfterActiveSelectionChanges() {
        Fixture oldVersion = fixture("old", "接口自动化", "repo-old", "release/old", true);
        when(configurationRepository.findWorkspace(oldVersion.template().workspaceId()))
                .thenReturn(Optional.of(oldVersion.template()));
        when(configurationRepository.findRepository(oldVersion.repository().repositoryId()))
                .thenReturn(Optional.of(oldVersion.repository()));

        var reference = service.resolveVersion(
                USER_ID,
                HOST_WORKSPACE_ID,
                oldVersion.template().workspaceId(),
                oldVersion.version().versionId());

        assertThat(reference.versionId()).isEqualTo(oldVersion.version().versionId());
        assertThat(reference.branch()).isEqualTo("release/old");
    }

    @Test
    void revokedMemberCannotResolveAutomationReferences() {
        when(configurationRepository.isActiveMember(APP_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.resolveActive(USER_ID, HOST_WORKSPACE_ID))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private Fixture fixture(
            String suffix,
            String workspaceName,
            String repositoryName,
            String branch,
            boolean ready) {
        CodeRepositoryId repositoryId = new CodeRepositoryId("repo_" + suffix);
        ApplicationWorkspaceId templateId = new ApplicationWorkspaceId("awp_" + suffix);
        ApplicationWorkspaceVersionId versionId = new ApplicationWorkspaceVersionId("awv_" + suffix);
        WorkspaceId runtimeWorkspaceId = new WorkspaceId("wrk_" + suffix);
        CodeRepository repository = repository(repositoryId, repositoryName, CodeRepositoryType.AUTOMATION_CODE_REPOSITORY);
        ApplicationWorkspace template = new ApplicationWorkspace(
                templateId,
                APP_ID,
                repositoryId,
                branch,
                "automation/" + suffix,
                workspaceName,
                true,
                NOW,
                NOW);
        ApplicationWorkspaceVersion version = mock(ApplicationWorkspaceVersion.class);
        when(version.versionId()).thenReturn(versionId);
        when(version.applicationWorkspaceId()).thenReturn(templateId);
        when(version.appId()).thenReturn(APP_ID);
        when(version.repositoryId()).thenReturn(repositoryId);
        when(version.version()).thenReturn("20260819");
        when(version.branch()).thenReturn(branch);
        when(version.workspaceRootPath()).thenReturn(
                "appworkspace:20260819/" + repositoryId.value() + "/automation/" + suffix);
        when(version.targetCommitHash()).thenReturn("commit-" + suffix);
        when(version.status()).thenReturn(ManagedWorkspaceStatus.ACTIVE);
        when(managedWorkspaceRepository.findVersion(versionId)).thenReturn(Optional.of(version));
        AutomationWorkspaceActiveVersion active = new AutomationWorkspaceActiveVersion(
                templateId, versionId, USER_ID, NOW, NOW, NOW);
        if (ready) {
            ApplicationWorkspaceVersionReplica replica = mock(ApplicationWorkspaceVersionReplica.class);
            when(replica.syncStatus()).thenReturn(WorkspaceReplicaSyncStatus.READY);
            when(replica.currentCommitHash()).thenReturn("commit-" + suffix);
            when(replica.runtimeWorkspaceId()).thenReturn(runtimeWorkspaceId);
            when(managedWorkspaceRepository.findVersionReplica(versionId, "server-a"))
                    .thenReturn(Optional.of(replica));
            when(workspaceRepository.findById(runtimeWorkspaceId)).thenReturn(Optional.of(new Workspace(
                    runtimeWorkspaceId,
                    workspaceName,
                    "automation:" + suffix,
                    WorkspaceStatus.ACTIVE,
                    NOW,
                    NOW,
                    "server-a",
                    "trace_" + suffix)));
            when(pathResolver.resolve("automation:" + suffix)).thenReturn(tempDir.resolve(suffix));
        }
        return new Fixture(repository, template, version, active);
    }

    private CodeRepository repository(
            CodeRepositoryId repositoryId,
            String name,
            CodeRepositoryType type) {
        return new CodeRepository(
                repositoryId,
                "https://git.example.test/" + repositoryId.value() + ".git",
                name,
                repositoryId.value(),
                type.value(),
                type.standard(),
                NOW,
                NOW);
    }

    private record Fixture(
            CodeRepository repository,
            ApplicationWorkspace template,
            ApplicationWorkspaceVersion version,
            AutomationWorkspaceActiveVersion active) {
    }
}
