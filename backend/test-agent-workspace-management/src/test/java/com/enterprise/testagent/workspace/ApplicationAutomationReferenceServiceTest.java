package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceReplica;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceState;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceOperationType;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceGenerationStatus;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.RepositoryRemoteTreeReader;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class ApplicationAutomationReferenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");
    private static final ApplicationId APP_ID = new ApplicationId("app_demo");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_automation");
    private static final UserId ADMIN_ID = new UserId("usr_admin");

    private ConfigurationManagementRepository configurationRepository;
    private ApplicationAutomationReferenceRepository automationRepository;
    private RepositoryRemoteTreeReader remoteTreeReader;
    private OpencodeProcessHeartbeatStore heartbeatStore;
    private GitWorkspaceService gitWorkspaceService;
    private ReferenceRepositoryReplicaTaskDispatcher taskDispatcher;
    private CommonParameterValues parameterValues;
    private CapturingPublisher publisher;
    private ApplicationAutomationReferenceService service;

    @BeforeEach
    void setUp() {
        configurationRepository = mock(ConfigurationManagementRepository.class);
        automationRepository = mock(ApplicationAutomationReferenceRepository.class);
        remoteTreeReader = mock(RepositoryRemoteTreeReader.class);
        heartbeatStore = mock(OpencodeProcessHeartbeatStore.class);
        gitWorkspaceService = mock(GitWorkspaceService.class);
        taskDispatcher = mock(ReferenceRepositoryReplicaTaskDispatcher.class);
        publisher = new CapturingPublisher();
        parameterValues = mock(CommonParameterValues.class);
        SshKeyEncryptionService sshKeyEncryptionService = mock(SshKeyEncryptionService.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(configurationRepository.findApplication(APP_ID)).thenReturn(Optional.of(application(APP_ID)));
        when(configurationRepository.isActiveMember(APP_ID, ADMIN_ID)).thenReturn(true);
        when(configurationRepository.findRepositoriesByApplication(APP_ID)).thenReturn(List.of(repository()));
        when(automationRepository.findByOperationId(eq(APP_ID), eq(REPOSITORY_ID), anyString()))
                .thenReturn(Optional.empty());
        when(automationRepository.findReplicas(eq(APP_ID), eq(REPOSITORY_ID), anyLong()))
                .thenReturn(List.of());
        when(automationRepository.findKnownServerIds(APP_ID, REPOSITORY_ID)).thenReturn(List.of());
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(new LinuxServerId("server-a")));
        when(taskDispatcher.dispatchScopedNow(anyString(), anyLong(), anyString(), any(), any()))
                .thenReturn(true);
        service = new ApplicationAutomationReferenceService(
                configurationRepository,
                automationRepository,
                remoteTreeReader,
                userRepository,
                heartbeatStore,
                parameterValues,
                gitWorkspaceService,
                sshKeyEncryptionService,
                new WorkspaceServerIdentity("server-a"),
                publisher,
                taskDispatcher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ReferenceRepositoryDirectoryMover.filesystem());
    }

    @Test
    void firstConfigurationCreatesOneRepositoryGenerationWithDefaultDescription() {
        when(remoteTreeReader.listTree(repository(), "feature/e2e", ADMIN_ID)).thenReturn(remoteTree());
        when(gitWorkspaceService.resolveRemoteBranchCommit(
                "https://git.example.test/automation.git", "feature/e2e", null)).thenReturn("commit-a");
        ApplicationAutomationReferenceState initial = state(APP_ID, null, null, 1L, 0L,
                ReferenceRepositoryStatus.UNINITIALIZED);
        when(automationRepository.ensureState(APP_ID, REPOSITORY_ID, "trace_configure", NOW))
                .thenReturn(initial);
        when(automationRepository.reserveGeneration(any(), eq(0L), eq(0L), eq(NOW)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        service.configure(
                APP_ID.value(),
                REPOSITORY_ID.value(),
                "feature/e2e",
                "src/test",
                " ",
                false,
                0L,
                "op_configure_1",
                ADMIN_ID,
                false,
                "trace_configure");

        ArgumentCaptor<ApplicationAutomationReferenceGeneration> generation =
                ArgumentCaptor.forClass(ApplicationAutomationReferenceGeneration.class);
        verify(automationRepository).reserveGeneration(generation.capture(), eq(0L), eq(0L), eq(NOW));
        assertThat(generation.getValue()).satisfies(saved -> {
            assertThat(saved.appId()).isEqualTo(APP_ID);
            assertThat(saved.repositoryId()).isEqualTo(REPOSITORY_ID);
            assertThat(saved.generation()).isEqualTo(1L);
            assertThat(saved.branch()).isEqualTo("feature/e2e");
            assertThat(saved.directoryPath()).isEqualTo("src/test");
            assertThat(saved.description())
                    .isEqualTo("自动化测试库 / feature/e2e / src/test，只读自动化引用");
            assertThat(saved.merge()).isFalse();
            assertThat(saved.targetCommitHash()).isEqualTo("commit-a");
        });
        verify(automationRepository).upsertTargets(
                APP_ID, REPOSITORY_ID, 1L, Set.of(new LinuxServerId("server-a")), NOW);
        assertThat(publisher.events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(ApplicationAutomationReferenceService.SYNC_REQUESTED_EVENT);
            assertThat(event.payload()).containsEntry("appId", APP_ID.value());
            assertThat(event.payload()).containsEntry("repositoryId", REPOSITORY_ID.value());
            assertThat(event.payload()).containsEntry("generation", 1L);
        });
    }

    @Test
    void staleExpectedGenerationIsRejectedBeforeResolvingRemoteHead() {
        when(remoteTreeReader.listTree(repository(), "main", ADMIN_ID)).thenReturn(remoteTree());
        when(automationRepository.ensureState(APP_ID, REPOSITORY_ID, "trace_stale", NOW))
                .thenReturn(state(APP_ID, 2L, null, 3L, 7L, ReferenceRepositoryStatus.READY));

        assertThatThrownBy(() -> service.configure(
                APP_ID.value(), REPOSITORY_ID.value(), "main", "src/test", "描述", false,
                1L, "op_stale", ADMIN_ID, false, "trace_stale"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("actualGeneration", 2L);
                });

        verify(gitWorkspaceService, never()).resolveRemoteBranchCommit(anyString(), anyString(), any());
        verify(automationRepository, never()).reserveGeneration(any(), anyLong(), anyLong(), any());
    }

    @Test
    void operationIdReplayIsIdempotentAndDoesNotStartAnotherSynchronization() {
        ApplicationAutomationReferenceGeneration existing = generation(APP_ID, 4L, "op_same");
        when(automationRepository.findByOperationId(APP_ID, REPOSITORY_ID, "op_same"))
                .thenReturn(Optional.of(existing));
        when(automationRepository.findState(APP_ID, REPOSITORY_ID))
                .thenReturn(Optional.of(state(APP_ID, 4L, null, 5L, 9L, ReferenceRepositoryStatus.READY)));
        when(automationRepository.findGeneration(APP_ID, REPOSITORY_ID, 4L)).thenReturn(Optional.of(existing));

        AutomationReferenceRepositoryResponses.Status response = service.configure(
                APP_ID.value(), REPOSITORY_ID.value(), "other", "other/path", "other", false,
                4L, "op_same", ADMIN_ID, false, "trace_replay");

        assertThat(response.activeGeneration()).isEqualTo(4L);
        verify(remoteTreeReader, never()).listTree(any(), anyString(), any());
        verify(automationRepository, never()).reserveGeneration(any(), anyLong(), anyLong(), any());
        verify(automationRepository, never()).upsertTargets(any(), any(), anyLong(), any(), any());
    }

    @Test
    void pathTraversalAndFileSelectionAreRejected() {
        assertThatThrownBy(() -> service.configure(
                APP_ID.value(), REPOSITORY_ID.value(), "main", "../secret", "描述", false,
                0L, "op_traversal", ADMIN_ID, false, "trace_traversal"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        when(remoteTreeReader.listTree(repository(), "main", ADMIN_ID)).thenReturn(remoteTree());
        assertThatThrownBy(() -> service.configure(
                APP_ID.value(), REPOSITORY_ID.value(), "main", "src/test/case.feature", "描述", false,
                0L, "op_file", ADMIN_ID, false, "trace_file"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(automationRepository, never()).ensureState(any(), any(), anyString(), any());
    }

    @Test
    void sameRepositoryHasIndependentStateInDifferentApplications() {
        ApplicationId otherApp = new ApplicationId("app_other");
        when(configurationRepository.findApplication(otherApp)).thenReturn(Optional.of(application(otherApp)));
        when(configurationRepository.isActiveMember(otherApp, ADMIN_ID)).thenReturn(true);
        when(configurationRepository.findRepositoriesByApplication(otherApp)).thenReturn(List.of(repository()));
        when(remoteTreeReader.listTree(repository(), "main", ADMIN_ID)).thenReturn(remoteTree());
        when(gitWorkspaceService.resolveRemoteBranchCommit(
                "https://git.example.test/automation.git", "main", null)).thenReturn("commit-main");
        when(automationRepository.ensureState(eq(APP_ID), eq(REPOSITORY_ID), anyString(), eq(NOW)))
                .thenReturn(state(APP_ID, null, null, 1L, 0L, ReferenceRepositoryStatus.UNINITIALIZED));
        when(automationRepository.ensureState(eq(otherApp), eq(REPOSITORY_ID), anyString(), eq(NOW)))
                .thenReturn(state(otherApp, null, null, 1L, 0L, ReferenceRepositoryStatus.UNINITIALIZED));
        when(automationRepository.reserveGeneration(any(), eq(0L), eq(0L), eq(NOW)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        service.configure(APP_ID.value(), REPOSITORY_ID.value(), "main", "src/test", "应用 A", false,
                0L, "op_app_a", ADMIN_ID, false, "trace_app_a");
        service.configure(otherApp.value(), REPOSITORY_ID.value(), "main", "src/test", "应用 B", false,
                0L, "op_app_b", ADMIN_ID, false, "trace_app_b");

        ArgumentCaptor<ApplicationAutomationReferenceGeneration> generations =
                ArgumentCaptor.forClass(ApplicationAutomationReferenceGeneration.class);
        verify(automationRepository, org.mockito.Mockito.times(2))
                .reserveGeneration(generations.capture(), eq(0L), eq(0L), eq(NOW));
        assertThat(generations.getAllValues())
                .extracting(ApplicationAutomationReferenceGeneration::appId)
                .containsExactly(APP_ID, otherApp);
    }

    @Test
    void currentReadyBranchUsesLocalSharedReplicaWithoutRemoteFetch(@TempDir Path referencesRoot) throws Exception {
        ApplicationAutomationReferenceGeneration current = generation(APP_ID, 4L, "op_current");
        when(automationRepository.findState(APP_ID, REPOSITORY_ID))
                .thenReturn(Optional.of(state(APP_ID, 4L, null, 5L, 9L, ReferenceRepositoryStatus.READY)));
        when(automationRepository.findGeneration(APP_ID, REPOSITORY_ID, 4L)).thenReturn(Optional.of(current));
        when(automationRepository.findReplicas(APP_ID, REPOSITORY_ID, 4L))
                .thenReturn(List.of(readyReplica(4L)));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(referencesRoot.toString()));

        String logicalRoot = service.logicalConfigurationPath(APP_ID, repository(), current);
        String relativeRoot = logicalRoot.substring("{env:OPENCODE_REFERENCES_DIR}/".length());
        Path replicaRoot = referencesRoot.resolve(relativeRoot.substring(0, relativeRoot.length() - "/src/test".length()));
        Files.createDirectories(replicaRoot.resolve("src/test/cases"));
        Files.createDirectories(replicaRoot.resolve(".git"));
        Files.writeString(replicaRoot.resolve("src/test/case.feature"), "Feature: local");
        Files.createSymbolicLink(replicaRoot.resolve("src/test/linked"), replicaRoot.resolve("src/test/cases"));

        List<RepositoryRemoteTreeReader.TreeNode> nodes = service.tree(
                APP_ID.value(), REPOSITORY_ID.value(), "main", "src/test", ADMIN_ID, false);

        assertThat(nodes).extracting(RepositoryRemoteTreeReader.TreeNode::name)
                .containsExactly("cases", "case.feature");
        verify(remoteTreeReader, never()).listTree(any(), anyString(), any());
    }

    private ApplicationDefinition application(ApplicationId appId) {
        return new ApplicationDefinition(appId, "Demo", true, NOW, NOW);
    }

    private CodeRepository repository() {
        return new CodeRepository(
                REPOSITORY_ID,
                "https://git.example.test/automation.git",
                "自动化测试库",
                "automation-tests",
                CodeRepositoryType.AUTOMATION_CODE_REPOSITORY.value(),
                "EXTERNAL",
                false,
                NOW,
                NOW);
    }

    private List<RepositoryRemoteTreeReader.TreeNode> remoteTree() {
        return List.of(new RepositoryRemoteTreeReader.TreeNode(
                "src",
                "src",
                "directory",
                List.of(new RepositoryRemoteTreeReader.TreeNode(
                        "test",
                        "src/test",
                        "directory",
                        List.of(new RepositoryRemoteTreeReader.TreeNode(
                                "case.feature", "src/test/case.feature", "file", List.of()))))));
    }

    private ApplicationAutomationReferenceState state(
            ApplicationId appId,
            Long activeGeneration,
            Long pendingGeneration,
            long nextGeneration,
            long lockVersion,
            ReferenceRepositoryStatus status) {
        return new ApplicationAutomationReferenceState(
                appId,
                REPOSITORY_ID,
                activeGeneration,
                pendingGeneration,
                nextGeneration,
                lockVersion,
                status,
                AutomationReferenceOperationType.CONFIGURE,
                "trace_state",
                null,
                NOW,
                NOW);
    }

    private ApplicationAutomationReferenceGeneration generation(
            ApplicationId appId, long value, String operationId) {
        return new ApplicationAutomationReferenceGeneration(
                appId,
                REPOSITORY_ID,
                value,
                "main",
                "src/test",
                "自动化测试",
                false,
                "commit-main",
                AutomationReferenceGenerationStatus.READY,
                AutomationReferenceOperationType.CONFIGURE,
                ADMIN_ID,
                operationId,
                "trace_generation",
                null,
                NOW,
                NOW,
                NOW);
    }

    private ApplicationAutomationReferenceReplica readyReplica(long generation) {
        return new ApplicationAutomationReferenceReplica(
                APP_ID,
                REPOSITORY_ID,
                generation,
                new LinuxServerId("server-a"),
                ReferenceRepositoryReplicaStatus.READY,
                "main",
                "commit-main",
                0,
                null,
                null,
                null,
                null,
                NOW,
                NOW,
                NOW,
                NOW);
    }

    private static final class CapturingPublisher implements ServerBroadcastPublisher {
        private final List<ServerBroadcastEvent> events = new ArrayList<>();

        @Override
        public String instanceId() {
            return "backend-a";
        }

        @Override
        public void publish(ServerBroadcastEvent event) {
            events.add(event);
        }
    }
}
