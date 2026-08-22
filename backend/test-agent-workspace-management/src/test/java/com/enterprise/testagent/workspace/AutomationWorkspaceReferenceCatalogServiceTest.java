package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceGenerationStatus;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceOperationType;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutomationWorkspaceReferenceCatalogServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T03:00:00Z");
    private static final ApplicationId APP_ID = new ApplicationId("app_demo");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_automation");
    private static final CodeRepositoryId HOST_REPOSITORY_ID = new CodeRepositoryId("repo_host");
    private static final UserId USER_ID = new UserId("usr_demo");
    private static final WorkspaceId HOST_WORKSPACE_ID = new WorkspaceId("wrk_host");

    @TempDir
    Path tempDir;

    private ConfigurationManagementRepository configurationRepository;
    private ManagedWorkspaceRepository managedWorkspaceRepository;
    private ApplicationAutomationReferenceService automationReferenceService;
    private ApplicationAutomationReferenceRepository automationRepository;
    private AutomationWorkspaceReferenceCatalogService service;

    @BeforeEach
    void setUp() {
        configurationRepository = mock(ConfigurationManagementRepository.class);
        managedWorkspaceRepository = mock(ManagedWorkspaceRepository.class);
        automationReferenceService = mock(ApplicationAutomationReferenceService.class);
        automationRepository = mock(ApplicationAutomationReferenceRepository.class);
        service = new AutomationWorkspaceReferenceCatalogService(
                configurationRepository, managedWorkspaceRepository, automationReferenceService, automationRepository);

        ApplicationWorkspaceVersion hostVersion = mock(ApplicationWorkspaceVersion.class);
        when(hostVersion.appId()).thenReturn(APP_ID);
        when(hostVersion.repositoryId()).thenReturn(HOST_REPOSITORY_ID);
        when(managedWorkspaceRepository.findVersionByRuntimeWorkspace(HOST_WORKSPACE_ID))
                .thenReturn(Optional.of(hostVersion));
        when(configurationRepository.findRepository(HOST_REPOSITORY_ID))
                .thenReturn(Optional.of(repository(HOST_REPOSITORY_ID, CodeRepositoryType.TEST_WORK_REPOSITORY)));
        when(configurationRepository.findApplication(APP_ID))
                .thenReturn(Optional.of(new ApplicationDefinition(APP_ID, "Demo", true, NOW, NOW)));
        when(configurationRepository.isActiveMember(APP_ID, USER_ID)).thenReturn(true);
        when(configurationRepository.findRepositoriesByApplication(APP_ID))
                .thenReturn(List.of(repository(REPOSITORY_ID, CodeRepositoryType.AUTOMATION_CODE_REPOSITORY)));
    }

    @Test
    void resolvesExactReadyGenerationWithoutExposingAnotherRepositoryOrApplication() {
        ApplicationAutomationReferenceGeneration generation = generation(3L);
        Path selectedDirectory = tempDir.resolve("scripts/e2e");
        when(automationReferenceService.requireGeneration(APP_ID, REPOSITORY_ID, 3L)).thenReturn(generation);
        when(automationReferenceService.requireReadyLocalDirectory(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(selectedDirectory);
        when(automationReferenceService.logicalConfigurationPath(
                APP_ID,
                repository(REPOSITORY_ID, CodeRepositoryType.AUTOMATION_CODE_REPOSITORY),
                generation))
                .thenReturn("{env:OPENCODE_REFERENCES_DIR}/automation/app_demo/repo_automation/3/scripts/e2e");
        when(automationReferenceService.isActiveGeneration(APP_ID, REPOSITORY_ID, 3L)).thenReturn(true);

        var reference = service.resolveGeneration(USER_ID, HOST_WORKSPACE_ID, APP_ID, REPOSITORY_ID, 3L);

        assertThat(reference.generation()).isEqualTo(3L);
        assertThat(reference.branch()).isEqualTo("release/e2e");
        assertThat(reference.directoryPath()).isEqualTo("scripts/e2e");
        assertThat(reference.workspaceRootPath()).isEqualTo(selectedDirectory.toString());
        assertThat(reference.configurationPath()).startsWith("{env:OPENCODE_REFERENCES_DIR}");
        assertThat(reference.current()).isTrue();
    }

    @Test
    void revokedMemberCannotResolveAutomationGeneration() {
        when(configurationRepository.isActiveMember(APP_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.resolveGeneration(
                USER_ID, HOST_WORKSPACE_ID, APP_ID, REPOSITORY_ID, 3L))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void locatorApplicationMustMatchHostWorkspaceApplication() {
        assertThatThrownBy(() -> service.resolveGeneration(
                USER_ID,
                HOST_WORKSPACE_ID,
                new ApplicationId("app_forged"),
                REPOSITORY_ID,
                3L))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void historicalGenerationRequiresServerIssuedLeaseBoundToTheOpeningUserAndWorkspace() {
        ApplicationAutomationReferenceGeneration generation = generation(3L);
        when(automationReferenceService.requireGeneration(APP_ID, REPOSITORY_ID, 3L)).thenReturn(generation);
        when(automationReferenceService.requireReadyLocalDirectory(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(tempDir);
        when(automationReferenceService.logicalConfigurationPath(any(), any(), any()))
                .thenReturn("{env:OPENCODE_REFERENCES_DIR}/automation/app/repo/3/scripts/e2e");
        when(automationReferenceService.isActiveGeneration(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(true, false, false, false);
        when(automationRepository.saveReadLease(
                anyString(), eq(USER_ID), eq(HOST_WORKSPACE_ID), eq(APP_ID), eq(REPOSITORY_ID),
                eq(3L), any(), any())).thenReturn(true);
        when(automationRepository.renewReadLease(
                anyString(), eq(USER_ID), eq(HOST_WORKSPACE_ID), eq(APP_ID), eq(REPOSITORY_ID),
                eq(3L), any(), any())).thenReturn(true);
        var opened = service.resolveGeneration(USER_ID, HOST_WORKSPACE_ID, APP_ID, REPOSITORY_ID, 3L);
        String token = service.issueReadLease(USER_ID, HOST_WORKSPACE_ID, opened);

        assertThat(service.resolveGeneration(
                USER_ID, HOST_WORKSPACE_ID, APP_ID, REPOSITORY_ID, 3L, token).generation()).isEqualTo(3L);
        assertThatThrownBy(() -> service.resolveGeneration(
                USER_ID, HOST_WORKSPACE_ID, APP_ID, REPOSITORY_ID, 3L, null))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.resolveGeneration(
                new UserId("usr_other"), HOST_WORKSPACE_ID, APP_ID, REPOSITORY_ID, 3L, token))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void reusesReadLeaseWhileFileTreeRebuildsTheSameCurrentReference() {
        ApplicationAutomationReferenceGeneration generation = generation(3L);
        when(automationReferenceService.requireGeneration(APP_ID, REPOSITORY_ID, 3L)).thenReturn(generation);
        when(automationReferenceService.requireReadyLocalDirectory(APP_ID, REPOSITORY_ID, 3L))
                .thenReturn(tempDir);
        when(automationReferenceService.logicalConfigurationPath(any(), any(), any()))
                .thenReturn("{env:OPENCODE_REFERENCES_DIR}/automation/app/repo/3/scripts/e2e");
        when(automationReferenceService.isActiveGeneration(APP_ID, REPOSITORY_ID, 3L)).thenReturn(true);
        when(automationRepository.saveReadLease(
                anyString(), eq(USER_ID), eq(HOST_WORKSPACE_ID), eq(APP_ID), eq(REPOSITORY_ID),
                eq(3L), any(), any())).thenReturn(true);
        var reference = service.resolveGeneration(USER_ID, HOST_WORKSPACE_ID, APP_ID, REPOSITORY_ID, 3L);

        String firstToken = service.issueReadLease(USER_ID, HOST_WORKSPACE_ID, reference);
        String secondToken = service.issueReadLease(USER_ID, HOST_WORKSPACE_ID, reference);

        assertThat(secondToken).isEqualTo(firstToken);
        verify(automationRepository, times(1)).saveReadLease(
                anyString(), eq(USER_ID), eq(HOST_WORKSPACE_ID), eq(APP_ID), eq(REPOSITORY_ID),
                eq(3L), any(), any());
    }

    private ApplicationAutomationReferenceGeneration generation(long generation) {
        return new ApplicationAutomationReferenceGeneration(
                APP_ID,
                REPOSITORY_ID,
                generation,
                "release/e2e",
                "scripts/e2e",
                "自动化代码库 / release/e2e / scripts/e2e，只读自动化引用",
                false,
                "abc123",
                AutomationReferenceGenerationStatus.READY,
                AutomationReferenceOperationType.CONFIGURE,
                USER_ID,
                "aar_12345678",
                "trace_catalog",
                null,
                NOW,
                NOW,
                NOW);
    }

    private CodeRepository repository(CodeRepositoryId repositoryId, CodeRepositoryType type) {
        return new CodeRepository(
                repositoryId,
                "https://git.example.test/" + repositoryId.value() + ".git",
                "自动化代码库",
                repositoryId.value(),
                type.value(),
                type.standard(),
                NOW,
                NOW);
    }
}
