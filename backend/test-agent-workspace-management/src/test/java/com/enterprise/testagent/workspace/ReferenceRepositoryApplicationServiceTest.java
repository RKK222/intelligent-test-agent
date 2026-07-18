package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplica;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryOperationType;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryRepository;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryState;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ReferenceRepositoryApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private static final UserId ADMIN = new UserId("usr_admin");
    private static final ApplicationId APP = new ApplicationId("app_demo");
    private static final CodeRepositoryId ASSET_ID = new CodeRepositoryId("repo_assets");

    @TempDir
    Path tempDir;

    private ConfigurationManagementRepository configurationRepository;
    private ReferenceRepositoryRepository referenceRepository;
    private UserRepository userRepository;
    private OpencodeProcessHeartbeatStore heartbeatStore;
    private CommonParameterValues parameterValues;
    private GitWorkspaceService gitWorkspaceService;
    private SshKeyEncryptionService sshKeyEncryptionService;
    private CapturingPublisher publisher;
    private ReferenceRepositoryApplicationService service;

    @BeforeEach
    void setUp() {
        configurationRepository = mock(ConfigurationManagementRepository.class);
        referenceRepository = mock(ReferenceRepositoryRepository.class);
        userRepository = mock(UserRepository.class);
        heartbeatStore = mock(OpencodeProcessHeartbeatStore.class);
        parameterValues = mock(CommonParameterValues.class);
        gitWorkspaceService = mock(GitWorkspaceService.class);
        sshKeyEncryptionService = mock(SshKeyEncryptionService.class);
        publisher = new CapturingPublisher();
        service = new ReferenceRepositoryApplicationService(
                configurationRepository,
                referenceRepository,
                userRepository,
                heartbeatStore,
                parameterValues,
                gitWorkspaceService,
                sshKeyEncryptionService,
                new WorkspaceServerIdentity("server-a"),
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(configurationRepository.findApplication(APP)).thenReturn(Optional.of(application()));
        when(userRepository.findByUserId(ADMIN)).thenReturn(Optional.of(admin()));
        when(referenceRepository.findReplicas(any())).thenReturn(List.of());
        when(referenceRepository.renewLease(any(), anyLong(), any(), anyString(), any(), any()))
                .thenReturn(true);
    }

    @Test
    void listsOnlyLinkedApplicationAssetRepositories() {
        when(configurationRepository.findRepositoriesByApplication(APP))
                .thenReturn(List.of(assetRepository(), codeRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.empty());
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.resolve("references/../references").toString()));

        List<ReferenceRepositoryResponses.Status> result = service.list(APP.value());

        assertThat(result).singleElement().satisfies(status -> {
            assertThat(status.repositoryId()).isEqualTo(ASSET_ID.value());
            assertThat(status.gitUrl()).isEqualTo("https://git.example.test/assets.git");
            assertThat(status.repositoryPath())
                    .isEqualTo(tempDir.resolve("references/assets").toAbsolutePath().normalize().toString());
            assertThat(status.status()).isEqualTo(ReferenceRepositoryStatus.UNINITIALIZED.name());
        });
        verify(referenceRepository, never()).findState(codeRepository().repositoryId());
    }

    @Test
    void listKeepsRepositoryVisibleWhenReferencesDirectoryParameterIsMissing() {
        when(configurationRepository.findRepositoriesByApplication(APP))
                .thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.empty());
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR")).thenReturn(Optional.empty());

        assertThat(service.list(APP.value()))
                .singleElement()
                .extracting(ReferenceRepositoryResponses.Status::repositoryPath)
                .isNull();
    }

    @Test
    void listKeepsLegacyRepositoryWithInvalidEnglishNameVisibleWithoutPath() {
        when(configurationRepository.findRepositoriesByApplication(APP))
                .thenReturn(List.of(assetRepository("legacy_name")));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.empty());
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.resolve("references").toString()));

        assertThat(service.list(APP.value()))
                .singleElement()
                .extracting(ReferenceRepositoryResponses.Status::repositoryPath)
                .isNull();
    }

    @Test
    void rejectsRepositoryThatIsNotLinkedToApplication() {
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(codeRepository()));

        assertThatThrownBy(() -> service.status(APP.value(), ASSET_ID.value()))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(referenceRepository, never()).findState(ASSET_ID);
    }

    @Test
    void initializesFirstGenerationWithDeduplicatedOnlineTargetsAndSafeBroadcastPayload() {
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.empty());
        when(gitWorkspaceService.resolveRemoteBranchCommit(
                "https://git.example.test/assets.git", "main", null)).thenReturn("commit-2");
        when(heartbeatStore.liveBackendServerIds())
                .thenReturn(Set.of(new LinuxServerId("server-a"), new LinuxServerId("server-b")));
        when(referenceRepository.initializeIfAbsent(any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        ReferenceRepositoryResponses.Status result = service.initialize(
                APP.value(), ASSET_ID.value(), "main", ADMIN, "trace_init");

        assertThat(result.generation()).isEqualTo(1L);
        assertThat(result.targetCommitHash()).isEqualTo("commit-2");
        assertThat(result.status()).isEqualTo(ReferenceRepositoryStatus.INITIALIZING.name());
        verify(referenceRepository).upsertTargets(
                eq(ASSET_ID),
                eq(1L),
                eq("main"),
                eq(Set.of(new LinuxServerId("server-a"), new LinuxServerId("server-b"))),
                eq(NOW));
        assertThat(publisher.events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(ReferenceRepositoryApplicationService.SYNC_REQUESTED_EVENT);
            assertThat(event.payload()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                    "repositoryId", ASSET_ID.value(),
                    "generation", 1L,
                    "traceId", "trace_init"));
        });
    }

    @Test
    void broadcastFailureDoesNotFailInitializedDatabaseState() {
        ServerBroadcastPublisher failingPublisher = mock(ServerBroadcastPublisher.class);
        when(failingPublisher.instanceId()).thenReturn("backend-failing");
        doThrow(new RuntimeException("sensitive-publisher-detail"))
                .when(failingPublisher).publish(any());
        ReferenceRepositoryApplicationService isolated = new ReferenceRepositoryApplicationService(
                configurationRepository,
                referenceRepository,
                userRepository,
                heartbeatStore,
                parameterValues,
                gitWorkspaceService,
                sshKeyEncryptionService,
                new WorkspaceServerIdentity("server-a"),
                failingPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.empty());
        when(gitWorkspaceService.resolveRemoteBranchCommit(
                "https://git.example.test/assets.git", "main", null)).thenReturn("commit-2");
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(new LinuxServerId("server-a")));
        when(referenceRepository.initializeIfAbsent(any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        ReferenceRepositoryResponses.Status result = isolated.initialize(
                APP.value(), ASSET_ID.value(), "main", ADMIN, "trace_broadcast_failure");

        assertThat(result.generation()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(ReferenceRepositoryStatus.INITIALIZING.name());
        verify(referenceRepository).upsertTargets(
                ASSET_ID, 1L, "main", Set.of(new LinuxServerId("server-a")), NOW);
    }

    @Test
    void activeInitializeIsIdempotentButBranchCannotChange() {
        ReferenceRepositoryState active = state("main", 2L, ReferenceRepositoryStatus.SYNCHRONIZING);
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(active));

        assertThat(service.initialize(APP.value(), ASSET_ID.value(), "main", ADMIN, "trace_retry").generation())
                .isEqualTo(2L);
        verify(gitWorkspaceService, never()).resolveRemoteBranchCommit(any(), any(), any());

        assertThatThrownBy(() -> service.initialize(
                APP.value(), ASSET_ID.value(), "release", ADMIN, "trace_switch"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void initializeCasLoserRejectsDifferentWinningBranchWithoutCreatingTargets() {
        ReferenceRepositoryState winner = new ReferenceRepositoryState(
                ASSET_ID,
                "release",
                "commit-release",
                1L,
                ReferenceRepositoryStatus.INITIALIZING,
                ADMIN,
                "trace_winner",
                null,
                NOW,
                NOW,
                NOW);
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(gitWorkspaceService.resolveRemoteBranchCommit(
                "https://git.example.test/assets.git", "main", null)).thenReturn("commit-main");
        when(referenceRepository.initializeIfAbsent(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initialize(
                APP.value(), ASSET_ID.value(), "main", ADMIN, "trace_loser"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(referenceRepository, never()).upsertTargets(any(), anyLong(), any(), any(), any());
        assertThat(publisher.events).isEmpty();
    }

    @Test
    void synchronizeStartsNewGenerationAndKeepsOfflineReplicaTargets() {
        ReferenceRepositoryState failed = state("main", 2L, ReferenceRepositoryStatus.FAILED);
        LinuxServerId online = new LinuxServerId("server-a");
        LinuxServerId offline = new LinuxServerId("server-offline");
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(failed));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(replica(offline, 2L)));
        when(gitWorkspaceService.resolveRemoteBranchCommit(
                "https://git.example.test/assets.git", "main", null)).thenReturn("commit-3");
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(online));
        when(referenceRepository.advanceGenerationIfCurrent(eq(2L), eq("main"), any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(2)));

        ReferenceRepositoryResponses.Status result = service.synchronize(
                APP.value(), ASSET_ID.value(), ADMIN, "trace_sync");

        assertThat(result.generation()).isEqualTo(3L);
        assertThat(result.targetCommitHash()).isEqualTo("commit-3");
        assertThat(result.status()).isEqualTo(ReferenceRepositoryStatus.SYNCHRONIZING.name());
        verify(referenceRepository).upsertTargets(
                ASSET_ID, 3L, "main", Set.of(online, offline), NOW);
        assertThat(result.operation()).isEqualTo(ReferenceRepositoryOperationType.SYNCHRONIZE.name());
    }

    @Test
    void switchesBranchAtFixedRemoteHeadAndKeepsHistoricalTargets() {
        ReferenceRepositoryState ready = state("main", 2L, ReferenceRepositoryStatus.READY);
        LinuxServerId online = new LinuxServerId("server-a");
        LinuxServerId historical = new LinuxServerId("server-offline");
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(ready));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(replica(historical, 2L)));
        when(gitWorkspaceService.resolveRemoteBranchCommit(
                "https://git.example.test/assets.git", "release", null)).thenReturn("commit-release");
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(online));
        when(referenceRepository.advanceGenerationIfCurrent(eq(2L), eq("main"), any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(2)));

        ReferenceRepositoryResponses.Status result = service.switchBranch(
                APP.value(), ASSET_ID.value(), "release", ADMIN, "trace_switch");

        assertThat(result.branch()).isEqualTo("release");
        assertThat(result.targetCommitHash()).isEqualTo("commit-release");
        assertThat(result.operation()).isEqualTo(ReferenceRepositoryOperationType.SWITCH_BRANCH.name());
        verify(referenceRepository).upsertTargets(
                ASSET_ID, 3L, "release", Set.of(online, historical), NOW);
    }

    @Test
    void switchRejectsSameBranchBeforeResolvingRemoteOrAdvancingGeneration() {
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 2L, ReferenceRepositoryStatus.READY)));

        assertThatThrownBy(() -> service.switchBranch(
                APP.value(), ASSET_ID.value(), "main", ADMIN, "trace_same"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(gitWorkspaceService, never()).resolveRemoteBranchCommit(any(), any(), any());
        verify(referenceRepository, never()).advanceGenerationIfCurrent(anyLong(), any(), any());
    }

    @Test
    void switchIsIdempotentOnlyForSameActiveSwitchAndConflictsWithOtherActivity() {
        ReferenceRepositoryState activeSwitch = new ReferenceRepositoryState(
                ASSET_ID, "release", "commit-release", 3L, ReferenceRepositoryStatus.SYNCHRONIZING,
                ReferenceRepositoryOperationType.SWITCH_BRANCH, ADMIN, "trace_active", null, NOW, NOW, NOW);
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(activeSwitch));

        assertThat(service.switchBranch(APP.value(), ASSET_ID.value(), "release", ADMIN, "trace_retry").generation())
                .isEqualTo(3L);
        assertThatThrownBy(() -> service.switchBranch(
                APP.value(), ASSET_ID.value(), "other", ADMIN, "trace_conflict"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(gitWorkspaceService, never()).resolveRemoteBranchCommit(any(), any(), any());
    }

    @Test
    void activeSwitchRetryRepairsAllTargetsAndRebroadcastsCurrentGeneration() {
        LinuxServerId online = new LinuxServerId("server-a");
        LinuxServerId historical = new LinuxServerId("server-offline");
        ReferenceRepositoryState activeSwitch = new ReferenceRepositoryState(
                ASSET_ID, "release", "commit-release", 3L, ReferenceRepositoryStatus.SYNCHRONIZING,
                ReferenceRepositoryOperationType.SWITCH_BRANCH, ADMIN, "trace_active", null, NOW, NOW, NOW);
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(activeSwitch));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(replica(historical, 2L)));
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(online));

        ReferenceRepositoryResponses.Status result = service.switchBranch(
                APP.value(), ASSET_ID.value(), "release", ADMIN, "trace_retry");

        assertThat(result.generation()).isEqualTo(3L);
        verify(referenceRepository).upsertTargets(
                ASSET_ID, 3L, "release", Set.of(online, historical), NOW);
        verify(referenceRepository).deferOfflineReplicas(ASSET_ID, 3L, Set.of(online), NOW);
        assertThat(publisher.events).singleElement().satisfies(event ->
                assertThat(event.payload().get("generation")).isEqualTo(3L));
    }

    @Test
    void switchRetryRepairsTargetsAfterFirstPostCasUpsertFailure() {
        LinuxServerId online = new LinuxServerId("server-a");
        LinuxServerId historical = new LinuxServerId("server-offline");
        ReferenceRepositoryState ready = state("main", 2L, ReferenceRepositoryStatus.READY);
        ReferenceRepositoryState activeSwitch = new ReferenceRepositoryState(
                ASSET_ID, "release", "commit-release", 3L, ReferenceRepositoryStatus.SYNCHRONIZING,
                ReferenceRepositoryOperationType.SWITCH_BRANCH, ADMIN, "trace_switch", null, NOW, NOW, NOW);
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(ready), Optional.of(activeSwitch));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(replica(historical, 2L)));
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(online));
        when(gitWorkspaceService.resolveRemoteBranchCommit(
                "https://git.example.test/assets.git", "release", null)).thenReturn("commit-release");
        when(referenceRepository.advanceGenerationIfCurrent(eq(2L), eq("main"), any()))
                .thenReturn(Optional.of(activeSwitch));
        doThrow(new RuntimeException("first target write failed"))
                .doNothing()
                .when(referenceRepository).upsertTargets(
                        ASSET_ID, 3L, "release", Set.of(online, historical), NOW);

        assertThatThrownBy(() -> service.switchBranch(
                APP.value(), ASSET_ID.value(), "release", ADMIN, "trace_switch"))
                .isInstanceOf(RuntimeException.class);
        assertThat(service.switchBranch(
                APP.value(), ASSET_ID.value(), "release", ADMIN, "trace_retry").generation()).isEqualTo(3L);

        verify(referenceRepository, times(2)).upsertTargets(
                ASSET_ID, 3L, "release", Set.of(online, historical), NOW);
        assertThat(publisher.events).hasSize(1);
    }

    @Test
    void missingSwitchBranchDoesNotAdvanceGeneration() {
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 2L, ReferenceRepositoryStatus.READY)));
        when(gitWorkspaceService.resolveRemoteBranchCommit(
                "https://git.example.test/assets.git", "missing", null))
                .thenThrow(new PlatformException(ErrorCode.GIT_UNAVAILABLE, "Git 远端分支不存在"));

        assertThatThrownBy(() -> service.switchBranch(
                APP.value(), ASSET_ID.value(), "missing", ADMIN, "trace_missing"))
                .isInstanceOf(PlatformException.class);
        verify(referenceRepository, never()).advanceGenerationIfCurrent(anyLong(), any(), any());
    }

    @Test
    void verifyStartsReadOnlyGenerationAndPreservesTargetAndHistoricalSnapshot() {
        ReferenceRepositoryState ready = state("main", 4L, ReferenceRepositoryStatus.READY);
        LinuxServerId online = new LinuxServerId("server-a");
        LinuxServerId offline = new LinuxServerId("server-b");
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(ready));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(replica(offline, 4L)));
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(online));
        when(referenceRepository.advanceGenerationIfCurrent(eq(4L), eq("main"), any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(2)));

        ReferenceRepositoryResponses.Status result = service.verify(
                APP.value(), ASSET_ID.value(), "trace_verify");

        assertThat(result.generation()).isEqualTo(5L);
        assertThat(result.targetCommitHash()).isEqualTo("commit-2");
        assertThat(result.operation()).isEqualTo(ReferenceRepositoryOperationType.VERIFY_POINTERS.name());
        verify(gitWorkspaceService, never()).fetchBranch(any(), anyString(), any());
        verify(referenceRepository).upsertTargets(
                ASSET_ID, 5L, "main", Set.of(online, offline), NOW);
    }

    @Test
    void activeVerifyRetryRepairsHistoricalTargetsAndRebroadcasts() {
        LinuxServerId historical = new LinuxServerId("server-offline");
        ReferenceRepositoryState activeVerify = new ReferenceRepositoryState(
                ASSET_ID, "main", "commit-2", 5L, ReferenceRepositoryStatus.VERIFYING,
                ReferenceRepositoryOperationType.VERIFY_POINTERS, ADMIN, "trace_verify", null, NOW, NOW, NOW);
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(activeVerify));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(replica(historical, 4L)));
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of());

        assertThat(service.verify(APP.value(), ASSET_ID.value(), "trace_retry").generation()).isEqualTo(5L);

        verify(referenceRepository).upsertTargets(
                ASSET_ID, 5L, "main", Set.of(historical), NOW);
        verify(referenceRepository).deferOfflineReplicas(ASSET_ID, 5L, Set.of(), NOW);
        assertThat(publisher.events).hasSize(1);
    }

    @Test
    void concurrentSynchronizeLoserReturnsWinningStateWithoutPublishingDuplicateWork() {
        ReferenceRepositoryState failed = state("main", 2L, ReferenceRepositoryStatus.FAILED);
        ReferenceRepositoryState winner = new ReferenceRepositoryState(
                ASSET_ID,
                "main",
                "commit-winner",
                3L,
                ReferenceRepositoryStatus.SYNCHRONIZING,
                ADMIN,
                "trace_winner",
                null,
                NOW,
                NOW,
                NOW);
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(failed), Optional.of(winner));
        when(gitWorkspaceService.resolveRemoteBranchCommit(
                "https://git.example.test/assets.git", "main", null)).thenReturn("commit-loser");
        when(referenceRepository.advanceGenerationIfCurrent(eq(2L), eq("main"), any())).thenReturn(Optional.empty());

        ReferenceRepositoryResponses.Status result = service.synchronize(
                APP.value(), ASSET_ID.value(), ADMIN, "trace_loser");

        assertThat(result.generation()).isEqualTo(3L);
        assertThat(result.targetCommitHash()).isEqualTo("commit-winner");
        assertThat(result.traceId()).isEqualTo("trace_winner");
        verify(referenceRepository, never()).upsertTargets(any(), anyLong(), any(), any(), any());
        assertThat(publisher.events).isEmpty();
    }

    @Test
    void reconciliationAddsLocalTargetAndClaimsDueReplicaThroughDatabaseLease() {
        ReferenceRepositoryState state = state("main", 4L, ReferenceRepositoryStatus.SYNCHRONIZING);
        ReferenceRepositoryReplica due = replica(new LinuxServerId("server-a"), 4L);
        LinuxServerId historical = new LinuxServerId("server-offline");
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(new LinuxServerId("server-a")));
        when(referenceRepository.findStatesAfter(null, 200)).thenReturn(List.of(state));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(replica(historical, 3L)));
        when(referenceRepository.findClaimableReplicas(new LinuxServerId("server-a"), NOW, 100))
                .thenReturn(List.of(due));
        when(referenceRepository.claimReplica(
                        eq(ASSET_ID),
                        eq(4L),
                        eq(new LinuxServerId("server-a")),
                        any(),
                        eq(NOW.plusSeconds(120)),
                        eq(NOW)))
                .thenReturn(Optional.empty());

        service.reconcileLocalReplicas("trace_reconcile");

        verify(referenceRepository).upsertTargets(
                ASSET_ID, 4L, "main", Set.of(new LinuxServerId("server-a"), historical), NOW);
        verify(referenceRepository).deferOfflineReplicas(
                ASSET_ID, 4L, Set.of(new LinuxServerId("server-a")), NOW);
        verify(referenceRepository).claimReplica(
                eq(ASSET_ID),
                eq(4L),
                eq(new LinuxServerId("server-a")),
                any(),
                eq(NOW.plusSeconds(120)),
                eq(NOW));
    }

    @Test
    void reconciliationDefersOfflineWorkerAndConvergesOnRemainingReadyServer() {
        LinuxServerId online = new LinuxServerId("server-a");
        LinuxServerId offline = new LinuxServerId("server-b");
        ReferenceRepositoryState state = state("main", 5L, ReferenceRepositoryStatus.SYNCHRONIZING);
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(online));
        when(referenceRepository.findStatesAfter(null, 200)).thenReturn(List.of(state));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(state));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(
                replica(online, 5L, ReferenceRepositoryReplicaStatus.READY),
                replica(offline, 5L, ReferenceRepositoryReplicaStatus.DEFERRED)));
        when(referenceRepository.findClaimableReplicas(online, NOW, 100)).thenReturn(List.of());

        service.reconcileLocalReplicas("trace_offline");

        verify(referenceRepository).deferOfflineReplicas(ASSET_ID, 5L, Set.of(online), NOW);
        verify(referenceRepository).upsertTargets(ASSET_ID, 5L, "main", Set.of(online, offline), NOW);
        verify(referenceRepository).updateOverallStatus(
                ASSET_ID, 5L, ReferenceRepositoryStatus.READY, null, NOW);
        verify(heartbeatStore, times(1)).liveBackendServerIds();
    }

    @Test
    void reconciliationWithNoLiveServersAdvancesHistoricalTargetsThenDefersWithoutClaiming() {
        ReferenceRepositoryState state = state("main", 6L, ReferenceRepositoryStatus.SYNCHRONIZING);
        LinuxServerId historical = new LinuxServerId("server-a");
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of());
        when(referenceRepository.findStatesAfter(null, 200)).thenReturn(List.of(state));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(
                replica(historical, 5L, ReferenceRepositoryReplicaStatus.READY)));

        service.reconcileLocalReplicas("trace_no_live");

        org.mockito.InOrder targetThenDefer = org.mockito.Mockito.inOrder(referenceRepository);
        targetThenDefer.verify(referenceRepository).upsertTargets(
                ASSET_ID, 6L, "main", Set.of(historical), NOW);
        targetThenDefer.verify(referenceRepository).deferOfflineReplicas(ASSET_ID, 6L, Set.of(), NOW);
        verify(referenceRepository).deferOfflineReplicas(ASSET_ID, 6L, Set.of(), NOW);
        verify(referenceRepository, never()).findClaimableReplicas(any(), any(), anyInt());
    }

    @Test
    void recoveredPendingServerReturnsOverallStateToSynchronizing() {
        LinuxServerId local = new LinuxServerId("server-a");
        LinuxServerId recovered = new LinuxServerId("server-b");
        ReferenceRepositoryState state = state("main", 7L, ReferenceRepositoryStatus.READY);
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(local, recovered));
        when(referenceRepository.findStatesAfter(null, 200)).thenReturn(List.of(state));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(state));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(
                replica(local, 7L, ReferenceRepositoryReplicaStatus.READY),
                replica(recovered, 7L, ReferenceRepositoryReplicaStatus.PENDING)));
        when(referenceRepository.findClaimableReplicas(local, NOW, 100)).thenReturn(List.of());

        service.reconcileLocalReplicas("trace_recovered");

        verify(referenceRepository).upsertTargets(
                ASSET_ID, 7L, "main", Set.of(local, recovered), NOW);
        verify(referenceRepository).updateOverallStatus(
                ASSET_ID, 7L, ReferenceRepositoryStatus.SYNCHRONIZING, null, NOW);
    }

    @Test
    void statusResponseIncludesDeferredReplica() {
        LinuxServerId deferred = new LinuxServerId("server-b");
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 8L, ReferenceRepositoryStatus.SYNCHRONIZING)));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(
                replica(deferred, 8L, ReferenceRepositoryReplicaStatus.DEFERRED)));

        ReferenceRepositoryResponses.Status response = service.status(APP.value(), ASSET_ID.value());

        assertThat(response.servers()).singleElement().satisfies(server -> {
            assertThat(server.linuxServerId()).isEqualTo("server-b");
            assertThat(server.status()).isEqualTo(ReferenceRepositoryReplicaStatus.DEFERRED.name());
        });
    }

    @Test
    void statusResponseReportsOperationOnlineMatchAndPointerTimestamps() {
        ReferenceRepositoryState ready = new ReferenceRepositoryState(
                ASSET_ID, "main", "commit-2", 8L, ReferenceRepositoryStatus.READY,
                ReferenceRepositoryOperationType.VERIFY_POINTERS, ADMIN, "trace_status", null, NOW, NOW, NOW);
        ReferenceRepositoryReplica observed = new ReferenceRepositoryReplica(
                ASSET_ID, new LinuxServerId("server-a"), 8L, ReferenceRepositoryReplicaStatus.READY,
                "main", "commit-2", 0, null, null, null, null,
                NOW.minusSeconds(60), NOW, NOW.minusSeconds(120), NOW);
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(ready));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(observed));
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(new LinuxServerId("server-a")));

        ReferenceRepositoryResponses.Status response = service.status(APP.value(), ASSET_ID.value());

        assertThat(response.operation()).isEqualTo(ReferenceRepositoryOperationType.VERIFY_POINTERS.name());
        assertThat(response.servers()).singleElement().satisfies(server -> {
            assertThat(server.online()).isTrue();
            assertThat(server.matchesTarget()).isTrue();
            assertThat(server.verifiedAt()).isEqualTo(NOW);
            assertThat(server.syncedAt()).isEqualTo(NOW.minusSeconds(60));
        });
    }

    @Test
    void statusDoesNotReportBlockedReplicaAsMatchingEvenWhenPointerEqualsTarget() {
        ReferenceRepositoryState failed = state("main", 8L, ReferenceRepositoryStatus.FAILED);
        ReferenceRepositoryReplica dirty = new ReferenceRepositoryReplica(
                ASSET_ID, new LinuxServerId("server-a"), 8L, ReferenceRepositoryReplicaStatus.BLOCKED,
                "main", "commit-2", 0, null, null, null, "引用资产本地仓库存在未提交修改",
                NOW.minusSeconds(60), NOW, NOW.minusSeconds(120), NOW);
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(failed));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(dirty));

        ReferenceRepositoryResponses.Status response = service.status(APP.value(), ASSET_ID.value());

        assertThat(response.servers()).singleElement().satisfies(server -> {
            assertThat(server.currentBranch()).isEqualTo("main");
            assertThat(server.currentCommitHash()).isEqualTo("commit-2");
            assertThat(server.matchesTarget()).isFalse();
        });
    }

    @Test
    void reconciliationUsesStableCursorToScanBeyondFirstStatePage() {
        LinuxServerId local = new LinuxServerId("server-a");
        List<ReferenceRepositoryState> firstPage = java.util.stream.IntStream.range(0, 200)
                .mapToObj(index -> pagedState("repo_page_%03d".formatted(index)))
                .toList();
        ReferenceRepositoryState finalState = pagedState("repo_page_200");
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(local));
        when(referenceRepository.findStatesAfter(null, 200)).thenReturn(firstPage);
        when(referenceRepository.findStatesAfter(firstPage.get(199).repositoryId(), 200))
                .thenReturn(List.of(finalState));
        when(referenceRepository.findClaimableReplicas(local, NOW, 100)).thenReturn(List.of());

        service.reconcileLocalReplicas("trace_pages");

        verify(referenceRepository).findStatesAfter(null, 200);
        verify(referenceRepository).findStatesAfter(firstPage.get(199).repositoryId(), 200);
        verify(referenceRepository).upsertTargets(
                finalState.repositoryId(), 1L, "main", Set.of(local), NOW);
    }

    @Test
    void broadcastClaimRetriesTransientGitFailureWithFiveSecondBackoff() {
        ReferenceRepositoryState state = state("main", 1L, ReferenceRepositoryStatus.INITIALIZING);
        when(referenceRepository.claimReplica(
                        eq(ASSET_ID),
                        eq(1L),
                        eq(new LinuxServerId("server-a")),
                        anyString(),
                        eq(NOW.plusSeconds(120)),
                        eq(NOW)))
                .thenAnswer(invocation -> Optional.of(processingReplica(
                        1L, invocation.getArgument(3, String.class))));
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(state));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));
        doThrow(new PlatformException(
                        ErrorCode.GIT_UNAVAILABLE,
                        "Git 暂时不可用",
                        java.util.Map.of("gitFailureType", "NETWORK_UNAVAILABLE")))
                .when(gitWorkspaceService).cloneBranch(anyString(), eq("main"), any(), isNull());
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(new LinuxServerId("server-a")));

        service.handle(syncEvent(1L));

        verify(referenceRepository).markRetry(
                eq(ASSET_ID),
                eq(1L),
                eq(new LinuxServerId("server-a")),
                anyString(),
                eq(1),
                eq(NOW.plusSeconds(5)),
                eq("Git 暂时不可用"),
                eq(NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUTHENTICATION_FAILED", "REPOSITORY_UNAVAILABLE", "BRANCH_NOT_FOUND", "UNKNOWN"})
    void permanentGitFailureBlocksReplicaInsteadOfAutomaticRetry(String failureType) {
        stubClaimedWorker(1L);
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 1L, ReferenceRepositoryStatus.INITIALIZING)));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));
        doThrow(new PlatformException(
                        ErrorCode.GIT_UNAVAILABLE,
                        "Git 永久失败",
                        java.util.Map.of("gitFailureType", failureType)))
                .when(gitWorkspaceService).cloneBranch(anyString(), eq("main"), any(), isNull());

        service.handle(syncEvent(1L));

        verify(referenceRepository).markBlocked(
                eq(ASSET_ID),
                eq(1L),
                eq(new LinuxServerId("server-a")),
                anyString(),
                eq("Git 永久失败"),
                eq(NOW));
        verify(referenceRepository, never()).markRetry(
                any(), anyLong(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void missingReferencesParameterBlocksReplicaInsteadOfRetryingForever() {
        stubClaimedWorker(1L);
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 1L, ReferenceRepositoryStatus.INITIALIZING)));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR")).thenReturn(Optional.empty());

        service.handle(syncEvent(1L));

        verify(referenceRepository).markBlocked(
                eq(ASSET_ID),
                eq(1L),
                eq(new LinuxServerId("server-a")),
                anyString(),
                eq("缺少引用资产根目录参数"),
                eq(NOW));
        verify(referenceRepository, never()).markRetry(
                any(), anyLong(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void expiredWorkerStopsBeforeCloneWhenLeaseRenewalFails() {
        stubClaimedWorker(1L);
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 1L, ReferenceRepositoryStatus.INITIALIZING)));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));
        when(referenceRepository.renewLease(
                        eq(ASSET_ID),
                        eq(1L),
                        eq(new LinuxServerId("server-a")),
                        anyString(),
                        eq(NOW.plusSeconds(120)),
                        eq(NOW)))
                .thenReturn(false);

        service.handle(syncEvent(1L));

        verify(gitWorkspaceService, never()).cloneBranch(any(), any(), any(), any());
        verify(referenceRepository, never()).markReady(
                any(), anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void localFileLockPreventsTwoJavaWorkersFromWritingSameReplicaConcurrently() throws Exception {
        stubClaimedWorker(1L);
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 1L, ReferenceRepositoryStatus.INITIALIZING)));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));
        when(referenceRepository.renewLease(any(), anyLong(), any(), anyString(), any(), any()))
                .thenReturn(true);
        when(referenceRepository.markRetry(any(), anyLong(), any(), anyString(), anyInt(), any(), any(), any()))
                .thenReturn(true);
        when(referenceRepository.markReady(any(), anyLong(), any(), anyString(), any(), any(), any(), any()))
                .thenReturn(true);
        when(gitWorkspaceService.resolveCommit(any(), eq("commit-2"))).thenReturn("commit-2");
        when(gitWorkspaceService.headCommit(any())).thenReturn("commit-2");
        CountDownLatch firstCloneEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstClone = new CountDownLatch(1);
        AtomicInteger cloneCalls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = cloneCalls.incrementAndGet();
            Path temporary = invocation.getArgument(2, Path.class);
            Files.createDirectories(temporary);
            if (call == 1) {
                firstCloneEntered.countDown();
                assertThat(releaseFirstClone.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(gitWorkspaceService).cloneBranch(anyString(), eq("main"), any(), isNull());
        ReferenceRepositoryApplicationService secondJavaProcess = new ReferenceRepositoryApplicationService(
                configurationRepository,
                referenceRepository,
                userRepository,
                heartbeatStore,
                parameterValues,
                gitWorkspaceService,
                sshKeyEncryptionService,
                new WorkspaceServerIdentity("server-a"),
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        var firstWorker = executor.submit(() -> service.handle(syncEvent(1L)));
        assertThat(firstCloneEntered.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            secondJavaProcess.handle(syncEvent(1L));
            assertThat(cloneCalls).hasValue(1);
        } finally {
            releaseFirstClone.countDown();
            firstWorker.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
        verify(gitWorkspaceService, times(1)).cloneBranch(anyString(), eq("main"), any(), isNull());
    }

    @Test
    void broadcastClaimClonesNewReplicaAtFixedCommitAndMovesItIntoPlace() throws Exception {
        when(referenceRepository.claimReplica(
                        eq(ASSET_ID),
                        eq(1L),
                        eq(new LinuxServerId("server-a")),
                        anyString(),
                        eq(NOW.plusSeconds(120)),
                        eq(NOW)))
                .thenAnswer(invocation -> Optional.of(processingReplica(
                        1L, invocation.getArgument(3, String.class))));
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 1L, ReferenceRepositoryStatus.INITIALIZING)));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));
        AtomicReference<Path> clonedTemporary = new AtomicReference<>();
        doAnswer(invocation -> {
            Path temporary = invocation.getArgument(2, Path.class);
            clonedTemporary.set(temporary);
            Files.createDirectories(temporary);
            Files.writeString(temporary.resolve("README.md"), "fixed generation");
            return null;
        }).when(gitWorkspaceService).cloneBranch(
                eq("https://git.example.test/assets.git"), eq("main"), any(), isNull());
        when(gitWorkspaceService.resolveCommit(any(), eq("commit-2"))).thenReturn("commit-2");
        when(gitWorkspaceService.headCommit(any())).thenReturn("commit-2");
        when(gitWorkspaceService.currentBranch(tempDir.resolve("assets"))).thenReturn("main");
        when(referenceRepository.markReady(
                        eq(ASSET_ID),
                        eq(1L),
                        eq(new LinuxServerId("server-a")),
                        anyString(),
                        eq("main"),
                        eq("commit-2"),
                        eq(NOW),
                        eq(NOW)))
                .thenReturn(true);

        service.handle(syncEvent(1L));

        Path repositoryRoot = tempDir.resolve("assets");
        assertThat(repositoryRoot.resolve("README.md")).hasContent("fixed generation");
        assertThat(clonedTemporary.get().getFileName().toString()).startsWith(".assets.1.");
        verify(gitWorkspaceService).resetHardToCommit(any(), eq("commit-2"));
        verify(referenceRepository).markReady(
                eq(ASSET_ID),
                eq(1L),
                eq(new LinuxServerId("server-a")),
                anyString(),
                eq("main"),
                eq("commit-2"),
                eq(NOW),
                eq(NOW));
    }

    @Test
    void synchronizationBlocksWhenPostWriteObservedPointerDoesNotMatchTarget() throws Exception {
        Path repositoryRoot = tempDir.resolve("assets");
        stubClaimedWorker(1L);
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 1L, ReferenceRepositoryStatus.INITIALIZING)));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR")).thenReturn(Optional.of(tempDir.toString()));
        doAnswer(invocation -> {
            Files.createDirectories(invocation.getArgument(2, Path.class));
            return null;
        }).when(gitWorkspaceService).cloneBranch(anyString(), eq("main"), any(), isNull());
        when(gitWorkspaceService.resolveCommit(any(), eq("commit-2"))).thenReturn("commit-2");
        when(gitWorkspaceService.headCommit(any())).thenAnswer(invocation ->
                invocation.getArgument(0, Path.class).equals(repositoryRoot) ? "commit-other" : "commit-2");
        when(gitWorkspaceService.currentBranch(repositoryRoot)).thenReturn("main");

        service.handle(syncEvent(1L));

        verify(referenceRepository).markBlocked(
                eq(ASSET_ID), eq(1L), eq(new LinuxServerId("server-a")), anyString(),
                eq("引用资产同步后实际指针与目标不一致"), eq(NOW));
        verify(referenceRepository, never()).markReady(
                any(), anyLong(), any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void switchWorkerChecksOutCleanCrossBranchRepositoryAtFixedCommit() throws Exception {
        Path repositoryRoot = tempDir.resolve("assets");
        Files.createDirectories(repositoryRoot);
        stubClaimedWorker(3L);
        ReferenceRepositoryState switching = new ReferenceRepositoryState(
                ASSET_ID, "release", "commit-release", 3L, ReferenceRepositoryStatus.SYNCHRONIZING,
                ReferenceRepositoryOperationType.SWITCH_BRANCH, ADMIN, "trace_switch", null, NOW, NOW, NOW);
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(switching));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR")).thenReturn(Optional.of(tempDir.toString()));
        when(gitWorkspaceService.isGitRepository(repositoryRoot)).thenReturn(true);
        when(gitWorkspaceService.isWorktreeClean(repositoryRoot)).thenReturn(true);
        when(gitWorkspaceService.originUrl(repositoryRoot)).thenReturn("https://git.example.test/assets.git");
        when(gitWorkspaceService.currentBranch(repositoryRoot)).thenReturn("release");
        when(gitWorkspaceService.headCommit(repositoryRoot)).thenReturn("commit-main", "commit-release");
        when(gitWorkspaceService.resolveCommit(repositoryRoot, "commit-release")).thenReturn("commit-release");
        when(referenceRepository.markReady(any(), anyLong(), any(), anyString(), any(), any(), any(), any()))
                .thenReturn(true);

        service.handle(syncEvent(3L));

        verify(gitWorkspaceService).fetchBranch(repositoryRoot, "release", null);
        verify(gitWorkspaceService).checkoutBranchForFixedCommit(
                repositoryRoot, "release", "commit-release", null);
        verify(referenceRepository).markReady(
                eq(ASSET_ID), eq(3L), eq(new LinuxServerId("server-a")), anyString(),
                eq("release"), eq("commit-release"), eq(NOW), eq(NOW));
    }

    @Test
    void switchWorkerRenewsLeaseBetweenCheckoutAndReset() throws Exception {
        Path repositoryRoot = tempDir.resolve("assets");
        Files.createDirectories(repositoryRoot);
        stubClaimedWorker(3L);
        ReferenceRepositoryState switching = new ReferenceRepositoryState(
                ASSET_ID, "release", "commit-release", 3L, ReferenceRepositoryStatus.SYNCHRONIZING,
                ReferenceRepositoryOperationType.SWITCH_BRANCH, ADMIN, "trace_switch", null, NOW, NOW, NOW);
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(switching));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR")).thenReturn(Optional.of(tempDir.toString()));
        when(gitWorkspaceService.isGitRepository(repositoryRoot)).thenReturn(true);
        when(gitWorkspaceService.isWorktreeClean(repositoryRoot)).thenReturn(true);
        when(gitWorkspaceService.originUrl(repositoryRoot)).thenReturn("https://git.example.test/assets.git");
        when(gitWorkspaceService.currentBranch(repositoryRoot)).thenReturn("main");
        when(gitWorkspaceService.headCommit(repositoryRoot)).thenReturn("commit-main");
        when(gitWorkspaceService.resolveCommit(repositoryRoot, "commit-release")).thenReturn("commit-release");
        when(referenceRepository.renewLease(any(), anyLong(), any(), anyString(), any(), any()))
                .thenReturn(true, true, true, false);

        service.handle(syncEvent(3L));

        verify(gitWorkspaceService).checkoutBranchForFixedCommit(
                repositoryRoot, "release", "commit-release", null);
        verify(gitWorkspaceService, never()).resetHardToCommit(repositoryRoot, "commit-release");
        verify(referenceRepository, never()).markReady(
                any(), anyLong(), any(), anyString(), any(), any(), any(), any());
    }

    @Test
    void verifyWorkerIsReadOnlyAndPersistsMatchingActualPointer() throws Exception {
        Path repositoryRoot = tempDir.resolve("assets");
        Files.createDirectories(repositoryRoot);
        stubClaimedWorker(4L);
        ReferenceRepositoryState verifying = new ReferenceRepositoryState(
                ASSET_ID, "main", "commit-2", 4L, ReferenceRepositoryStatus.VERIFYING,
                ReferenceRepositoryOperationType.VERIFY_POINTERS, ADMIN, "trace_verify", null, NOW, NOW, NOW);
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(verifying));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR")).thenReturn(Optional.of(tempDir.toString()));
        when(gitWorkspaceService.isGitRepository(repositoryRoot)).thenReturn(true);
        when(gitWorkspaceService.currentBranch(repositoryRoot)).thenReturn("main");
        when(gitWorkspaceService.headCommit(repositoryRoot)).thenReturn("commit-2");
        when(gitWorkspaceService.originUrl(repositoryRoot)).thenReturn("https://git.example.test/assets.git");
        when(gitWorkspaceService.isWorktreeCleanReadOnly(repositoryRoot)).thenReturn(true);
        when(referenceRepository.markVerificationResult(any(), anyLong(), any(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        service.handle(syncEvent(4L));

        verify(referenceRepository).markVerificationResult(
                eq(ASSET_ID), eq(4L), eq(new LinuxServerId("server-a")), anyString(),
                eq(ReferenceRepositoryReplicaStatus.READY), eq("main"), eq("commit-2"),
                eq(NOW), isNull(), eq(NOW));
        verify(gitWorkspaceService, never()).fetchBranch(any(), anyString(), any());
        verify(gitWorkspaceService, never()).resetHardToCommit(any(), any());
        verify(gitWorkspaceService, never()).checkoutBranchForFixedCommit(any(), any(), any(), any());
        verify(gitWorkspaceService, never()).cloneBranch(any(), any(), any(), any());
        verify(gitWorkspaceService, never()).setOriginUrl(any(), any(), any());
        verify(gitWorkspaceService).isWorktreeCleanReadOnly(repositoryRoot);
        verify(gitWorkspaceService, never()).isWorktreeClean(repositoryRoot);
    }

    @Test
    void verifyWorkerBlocksMismatchAndPersistsObservedPointer() throws Exception {
        Path repositoryRoot = tempDir.resolve("assets");
        Files.createDirectories(repositoryRoot);
        stubClaimedWorker(5L);
        ReferenceRepositoryState verifying = new ReferenceRepositoryState(
                ASSET_ID, "main", "commit-2", 5L, ReferenceRepositoryStatus.VERIFYING,
                ReferenceRepositoryOperationType.VERIFY_POINTERS, ADMIN, "trace_verify", null, NOW, NOW, NOW);
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(verifying));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR")).thenReturn(Optional.of(tempDir.toString()));
        when(gitWorkspaceService.isGitRepository(repositoryRoot)).thenReturn(true);
        when(gitWorkspaceService.currentBranch(repositoryRoot)).thenReturn("release");
        when(gitWorkspaceService.headCommit(repositoryRoot)).thenReturn("commit-other");
        when(gitWorkspaceService.originUrl(repositoryRoot)).thenReturn("https://git.example.test/assets.git");
        when(gitWorkspaceService.isWorktreeCleanReadOnly(repositoryRoot)).thenReturn(true);
        when(referenceRepository.markVerificationResult(any(), anyLong(), any(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        service.handle(syncEvent(5L));

        verify(referenceRepository).markVerificationResult(
                eq(ASSET_ID), eq(5L), eq(new LinuxServerId("server-a")), anyString(),
                eq(ReferenceRepositoryReplicaStatus.BLOCKED), eq("release"), eq("commit-other"),
                eq(NOW), eq("引用资产本地实际指针与目标不一致"), eq(NOW));
    }

    @Test
    void verifyReadFailurePreservesPreviousSnapshotAndVerificationTime() throws Exception {
        Path repositoryRoot = tempDir.resolve("assets");
        Files.createDirectories(repositoryRoot);
        String leaseToken = "lease-verify";
        ReferenceRepositoryReplica claimed = new ReferenceRepositoryReplica(
                ASSET_ID, new LinuxServerId("server-a"), 6L, ReferenceRepositoryReplicaStatus.PROCESSING,
                "main", "commit-old", 0, null, leaseToken, NOW.plusSeconds(120), null,
                NOW.minusSeconds(120), NOW.minusSeconds(60), NOW.minusSeconds(360), NOW);
        when(referenceRepository.claimReplica(
                eq(ASSET_ID), eq(6L), eq(new LinuxServerId("server-a")), anyString(),
                eq(NOW.plusSeconds(120)), eq(NOW))).thenReturn(Optional.of(claimed));
        ReferenceRepositoryState verifying = new ReferenceRepositoryState(
                ASSET_ID, "main", "commit-2", 6L, ReferenceRepositoryStatus.VERIFYING,
                ReferenceRepositoryOperationType.VERIFY_POINTERS, ADMIN, "trace_verify", null, NOW, NOW, NOW);
        when(referenceRepository.findState(ASSET_ID)).thenReturn(Optional.of(verifying));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR")).thenReturn(Optional.of(tempDir.toString()));
        when(gitWorkspaceService.isGitRepository(repositoryRoot)).thenReturn(true);
        when(gitWorkspaceService.currentBranch(repositoryRoot)).thenReturn("release");
        when(gitWorkspaceService.headCommit(repositoryRoot)).thenThrow(new PlatformException(
                ErrorCode.GIT_UNAVAILABLE, "HEAD 读取失败"));

        service.handle(syncEvent(6L));

        verify(referenceRepository).markBlocked(
                eq(ASSET_ID), eq(6L), eq(new LinuxServerId("server-a")), eq(leaseToken),
                eq("引用资产本地指针核验失败"), eq(NOW));
        verify(referenceRepository, never()).markVerificationResult(
                any(), anyLong(), any(), anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void atomicMoveUnsupportedBlocksReplicaWithoutFallingBackToOrdinaryMove() throws Exception {
        stubClaimedWorker(1L);
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 1L, ReferenceRepositoryStatus.INITIALIZING)));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));
        doAnswer(invocation -> {
            Path temporary = invocation.getArgument(2, Path.class);
            Files.createDirectories(temporary);
            Files.writeString(temporary.resolve("README.md"), "must not be moved non-atomically");
            return null;
        }).when(gitWorkspaceService).cloneBranch(anyString(), eq("main"), any(), isNull());
        when(gitWorkspaceService.resolveCommit(any(), eq("commit-2"))).thenReturn("commit-2");
        when(gitWorkspaceService.headCommit(any())).thenReturn("commit-2");
        ReferenceRepositoryApplicationService nonAtomicFilesystem = new ReferenceRepositoryApplicationService(
                configurationRepository,
                referenceRepository,
                userRepository,
                heartbeatStore,
                parameterValues,
                gitWorkspaceService,
                sshKeyEncryptionService,
                new WorkspaceServerIdentity("server-a"),
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                (source, target) -> {
                    throw new AtomicMoveNotSupportedException(
                            source.toString(), target.toString(), "atomic rename unavailable");
                });

        nonAtomicFilesystem.handle(syncEvent(1L));

        verify(referenceRepository).markBlocked(
                eq(ASSET_ID),
                eq(1L),
                eq(new LinuxServerId("server-a")),
                anyString(),
                eq("引用资产根目录不支持原子移动"),
                eq(NOW));
        assertThat(tempDir.resolve("assets")).doesNotExist();
    }

    @Test
    void broadcastClaimBlocksUnknownExistingDirectoryWithoutDeletingIt() throws Exception {
        Path repositoryRoot = tempDir.resolve("assets");
        Files.createDirectories(repositoryRoot);
        Files.writeString(repositoryRoot.resolve("keep.txt"), "do not delete");
        when(referenceRepository.claimReplica(
                        eq(ASSET_ID),
                        eq(1L),
                        eq(new LinuxServerId("server-a")),
                        anyString(),
                        eq(NOW.plusSeconds(120)),
                        eq(NOW)))
                .thenAnswer(invocation -> Optional.of(processingReplica(
                        1L, invocation.getArgument(3, String.class))));
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 1L, ReferenceRepositoryStatus.INITIALIZING)));
        when(configurationRepository.findRepository(ASSET_ID)).thenReturn(Optional.of(assetRepository()));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));
        when(gitWorkspaceService.isGitRepository(repositoryRoot)).thenReturn(false);
        when(heartbeatStore.liveBackendServerIds()).thenReturn(Set.of(new LinuxServerId("server-a")));

        service.handle(syncEvent(1L));

        verify(referenceRepository).markBlocked(
                eq(ASSET_ID),
                eq(1L),
                eq(new LinuxServerId("server-a")),
                anyString(),
                eq("引用资产目标目录不是可接管的 Git 仓库"),
                eq(NOW));
        assertThat(repositoryRoot.resolve("keep.txt")).exists();
    }

    @Test
    void treeMarksOnlyConfiguredRootFoldersAndRejectsTraversalGitAndSymlink() throws Exception {
        Path repositoryRoot = tempDir.resolve("assets");
        Files.createDirectories(repositoryRoot.resolve("docs"));
        Files.createDirectories(repositoryRoot.resolve("nested/docs"));
        Files.writeString(repositoryRoot.resolve("README.md"), "reference assets");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Files.createSymbolicLink(repositoryRoot.resolve("escape"), outside);
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 3L, ReferenceRepositoryStatus.READY)));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(replicaReady(3L)));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));
        when(parameterValues.resolvedValue("REFERENCES_SDD_FOLDER_NAMES"))
                .thenReturn(Optional.of("docs,spec"));

        List<ReferenceRepositoryResponses.TreeNode> root = service.tree(APP.value(), ASSET_ID.value(), "");
        List<ReferenceRepositoryResponses.TreeNode> nested = service.tree(APP.value(), ASSET_ID.value(), "nested");

        assertThat(root).filteredOn(node -> node.name().equals("docs")).singleElement().satisfies(node -> {
            assertThat(node.highlighted()).isTrue();
            assertThat(node.selectable()).isTrue();
        });
        assertThat(root).extracting(ReferenceRepositoryResponses.TreeNode::name).doesNotContain("escape");
        assertThat(nested).singleElement().satisfies(node -> {
            assertThat(node.name()).isEqualTo("docs");
            assertThat(node.highlighted()).isFalse();
            assertThat(node.selectable()).isFalse();
        });
        for (String unsafe : List.of("../outside", repositoryRoot.toString(), ".git", "escape")) {
            assertThatThrownBy(() -> service.tree(APP.value(), ASSET_ID.value(), unsafe))
                    .isInstanceOf(PlatformException.class);
        }
    }

    @Test
    void rejectsMissingOrUnsafeRepositoryEnglishNameBeforeResolvingFilesystemPath() {
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 3L, ReferenceRepositoryStatus.READY)));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(replicaReady(3L)));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));

        for (String englishName : java.util.Arrays.asList(null, "../escape", "-assets", "assets_unsafe")) {
            when(configurationRepository.findRepositoriesByApplication(APP))
                    .thenReturn(List.of(assetRepository(englishName)));

            assertThatThrownBy(() -> service.tree(APP.value(), ASSET_ID.value(), ""))
                    .isInstanceOfSatisfying(PlatformException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }
        assertThat(tempDir.resolve("escape")).doesNotExist();
    }

    @Test
    void treeReturnsAtMostOneThousandEntriesPerLevel() throws Exception {
        Path repositoryRoot = tempDir.resolve("assets");
        Files.createDirectories(repositoryRoot);
        for (int index = 0; index < 1001; index++) {
            Files.writeString(repositoryRoot.resolve("entry-%04d.txt".formatted(index)), "x");
        }
        when(configurationRepository.findRepositoriesByApplication(APP)).thenReturn(List.of(assetRepository()));
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 3L, ReferenceRepositoryStatus.READY)));
        when(referenceRepository.findReplicas(ASSET_ID)).thenReturn(List.of(replicaReady(3L)));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));

        assertThat(service.tree(APP.value(), ASSET_ID.value(), "")).hasSize(1000);
    }

    @Test
    void unsafeEnglishNameDoesNotCreateReplicaLockPath() {
        stubClaimedWorker(1L);
        when(referenceRepository.findState(ASSET_ID))
                .thenReturn(Optional.of(state("main", 1L, ReferenceRepositoryStatus.INITIALIZING)));
        when(configurationRepository.findRepository(ASSET_ID))
                .thenReturn(Optional.of(assetRepository("../escape")));
        when(parameterValues.resolvedValue("OPENCODE_REFERENCES_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));

        service.handle(syncEvent(1L));

        assertThat(tempDir.resolve(".reference-repository-locks")).doesNotExist();
        assertThat(tempDir.resolve("escape")).doesNotExist();
    }

    private ApplicationDefinition application() {
        return new ApplicationDefinition(APP, "Demo", true, NOW, NOW);
    }

    private CodeRepository assetRepository() {
        return assetRepository("assets");
    }

    private CodeRepository assetRepository(String englishName) {
        return new CodeRepository(
                ASSET_ID,
                "https://git.example.test/assets.git",
                "资产库",
                englishName,
                CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value(),
                "EXTERNAL",
                false,
                NOW,
                NOW);
    }

    private CodeRepository codeRepository() {
        return new CodeRepository(
                new CodeRepositoryId("repo_code"),
                "https://git.example.test/code.git",
                "代码库",
                "code",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                "EXTERNAL",
                false,
                NOW,
                NOW);
    }

    private User admin() {
        return new User(ADMIN, "001", "admin", "hash", null, null, null, UserStatus.ACTIVE, NOW, NOW);
    }

    private ReferenceRepositoryState state(String branch, long generation, ReferenceRepositoryStatus status) {
        return new ReferenceRepositoryState(
                ASSET_ID, branch, "commit-2", generation, status, ADMIN, "trace_state", null, NOW, NOW, NOW);
    }

    private ReferenceRepositoryState pagedState(String repositoryId) {
        return new ReferenceRepositoryState(
                new CodeRepositoryId(repositoryId),
                "main",
                "commit-1",
                1L,
                ReferenceRepositoryStatus.READY,
                ADMIN,
                "trace_page",
                null,
                NOW,
                NOW,
                NOW);
    }

    private ReferenceRepositoryReplica replicaReady(long generation) {
        return new ReferenceRepositoryReplica(
                ASSET_ID,
                new LinuxServerId("server-a"),
                generation,
                ReferenceRepositoryReplicaStatus.READY,
                "main",
                "commit-2",
                0,
                null,
                null,
                null,
                null,
                NOW,
                NOW,
                NOW);
    }

    private ReferenceRepositoryReplica replica(LinuxServerId serverId, long generation) {
        return replica(serverId, generation, ReferenceRepositoryReplicaStatus.PENDING);
    }

    private ReferenceRepositoryReplica replica(
            LinuxServerId serverId,
            long generation,
            ReferenceRepositoryReplicaStatus status) {
        return new ReferenceRepositoryReplica(
                ASSET_ID,
                serverId,
                generation,
                status,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                NOW,
                NOW);
    }

    private ReferenceRepositoryReplica processingReplica(long generation, String leaseToken) {
        return new ReferenceRepositoryReplica(
                ASSET_ID,
                new LinuxServerId("server-a"),
                generation,
                ReferenceRepositoryReplicaStatus.PROCESSING,
                "main",
                null,
                0,
                null,
                leaseToken,
                NOW.plusSeconds(120),
                null,
                null,
                NOW,
                NOW);
    }

    private void stubClaimedWorker(long generation) {
        when(referenceRepository.claimReplica(
                        eq(ASSET_ID),
                        eq(generation),
                        eq(new LinuxServerId("server-a")),
                        anyString(),
                        eq(NOW.plusSeconds(120)),
                        eq(NOW)))
                .thenAnswer(invocation -> Optional.of(processingReplica(
                        generation, invocation.getArgument(3, String.class))));
    }

    private ServerBroadcastEvent syncEvent(long generation) {
        return new ServerBroadcastEvent(
                "evt_reference_sync",
                ReferenceRepositoryApplicationService.SYNC_REQUESTED_EVENT,
                "backend-a",
                "server-a",
                "trace_event",
                NOW,
                java.util.Map.of(
                        "repositoryId", ASSET_ID.value(),
                        "generation", generation,
                        "traceId", "trace_event"));
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
