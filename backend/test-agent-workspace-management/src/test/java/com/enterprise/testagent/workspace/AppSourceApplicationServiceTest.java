package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitRemoteService;
import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.appsource.AppSourceCleanupStatus;
import com.enterprise.testagent.domain.appsource.AppSourceCleanupTask;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceRecentSelection;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.appsource.AppSourceStepScope;
import com.enterprise.testagent.domain.appsource.AppSourceStepStatus;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppSourceApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T04:00:00Z");
    private static final ApplicationId APP_ID = new ApplicationId("app_1");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_source");
    private static final UserId USER_ID = new UserId("usr_1");
    private static final String COMMIT = "b".repeat(40);

    private ConfigurationManagementRepository configuration;
    private AppSourceRepository appSources;
    private UserRepository users;
    private OpencodeProcessManagementRepository processes;
    private OpencodeProcessHeartbeatStore heartbeats;
    private GitRemoteService remote;
    private GitWorkspaceService git;
    private AppSourceMaterializationRegistrar registrar;
    private AppSourceReplicaRetryRegistrar retryRegistrar;
    private AppSourceReplicaTaskDispatcher dispatcher;
    private AppSourceWorkspaceOpener workspaceOpener;
    private AppSourceApplicationService service;

    @BeforeEach
    void setUp() {
        configuration = mock(ConfigurationManagementRepository.class);
        appSources = mock(AppSourceRepository.class);
        users = mock(UserRepository.class);
        processes = mock(OpencodeProcessManagementRepository.class);
        heartbeats = mock(OpencodeProcessHeartbeatStore.class);
        remote = mock(GitRemoteService.class);
        git = mock(GitWorkspaceService.class);
        registrar = mock(AppSourceMaterializationRegistrar.class);
        retryRegistrar = mock(AppSourceReplicaRetryRegistrar.class);
        dispatcher = mock(AppSourceReplicaTaskDispatcher.class);
        workspaceOpener = mock(AppSourceWorkspaceOpener.class);
        when(configuration.findApplication(APP_ID)).thenReturn(Optional.of(
                new ApplicationDefinition(APP_ID, "Billing", true, NOW.minusSeconds(100), NOW)));
        when(configuration.isActiveMember(APP_ID, USER_ID)).thenReturn(true);
        when(configuration.findRepositoriesByApplication(APP_ID)).thenReturn(List.of(repository()));
        when(configuration.findRepository(REPOSITORY_ID)).thenReturn(Optional.of(repository()));
        when(configuration.findApplicationsByRepository(REPOSITORY_ID)).thenReturn(List.of(
                new ApplicationDefinition(APP_ID, "Billing", true, NOW.minusSeconds(100), NOW)));
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.empty());
        when(appSources.findActiveSnapshot(REPOSITORY_ID)).thenReturn(Optional.empty());
        when(git.resolveRemoteBranchCommit("/git/repo.git", "main", null)).thenReturn(COMMIT);
        when(remote.listTreeWithCommitOrBranchFallback("/git/repo.git", COMMIT, "main", null)).thenReturn(List.of(
                new GitRemoteService.RemoteTreeNode("src", "src", GitRemoteService.NODE_TYPE_DIRECTORY, List.of(
                        new GitRemoteService.RemoteTreeNode(
                                "Main.java", "src/Main.java", GitRemoteService.NODE_TYPE_FILE, List.of())))));
        service = new AppSourceApplicationService(
                configuration,
                appSources,
                users,
                processes,
                heartbeats,
                remote,
                git,
                mock(SshKeyEncryptionService.class),
                registrar,
                retryRegistrar,
                dispatcher,
                workspaceOpener,
                new AppSourceIndexManager(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void treeSnapshotResolvesBranchOnceAndListsTheSameFixedCommit() {
        AppSourceApplicationService.TreeSnapshot snapshot = service.getTreeSnapshot(
                APP_ID.value(), REPOSITORY_ID.value(), "main", ".", USER_ID);

        assertThat(snapshot.targetCommit()).isEqualTo(COMMIT);
        assertThat(snapshot.nodes()).singleElement().satisfies(node -> {
            assertThat(node.path()).isEqualTo("src");
            assertThat(node.children()).singleElement()
                    .extracting(GitRemoteService.RemoteTreeNode::path)
                    .isEqualTo("src/Main.java");
        });
        verify(git).resolveRemoteBranchCommit("/git/repo.git", "main", null);
        verify(remote).listTreeWithCommitOrBranchFallback("/git/repo.git", COMMIT, "main", null);
    }

    @Test
    void emptyDirectoryTreeSnapshotStillReturnsTheFullFixedCommit() {
        when(remote.listTreeWithCommitOrBranchFallback("/git/repo.git", COMMIT, "main", null)).thenReturn(List.of(
                new GitRemoteService.RemoteTreeNode(
                        "empty", "empty", GitRemoteService.NODE_TYPE_DIRECTORY, List.of())));

        AppSourceApplicationService.TreeSnapshot snapshot = service.getTreeSnapshot(
                APP_ID.value(), REPOSITORY_ID.value(), "main", "empty", USER_ID);

        assertThat(snapshot.targetCommit()).isEqualTo(COMMIT).hasSize(40);
        assertThat(snapshot.nodes()).isEmpty();
        verify(git).resolveRemoteBranchCommit("/git/repo.git", "main", null);
        verify(remote).listTreeWithCommitOrBranchFallback("/git/repo.git", COMMIT, "main", null);
    }

    @Test
    void teamMaterializationFreezesOnlyServersLiveAtAcceptanceAndDispatchesAfterRegistration() {
        when(heartbeats.liveBackendServerIds()).thenReturn(Set.of(
                new LinuxServerId("server-b"), new LinuxServerId("server-a")));
        when(registrar.register(any())).thenAnswer(invocation -> {
            AppSourceMaterializationRegistrar.RegistrationRequest request = invocation.getArgument(0);
            AppSourceOperation operation = new AppSourceOperation(
                    request.operationId(), request.appId(), request.repositoryId(), null, 1L, request.actorUserId(),
                    AppSourceOperationType.DOWNLOAD, request.requestHash(), AppSourceOperationStatus.PENDING,
                    request.traceId(), NOW, null);
            return new AppSourceMaterializationRegistrar.RegistrationResult(operation, request.targetServerIds());
        });

        AppSourceOperation operation = service.materialize(
                APP_ID.value(), REPOSITORY_ID.value(), command(AppSourcePurpose.TEAM), USER_ID, false, "trace_source");

        ArgumentCaptor<AppSourceMaterializationRegistrar.RegistrationRequest> requestCaptor =
                ArgumentCaptor.forClass(AppSourceMaterializationRegistrar.RegistrationRequest.class);
        verify(registrar).register(requestCaptor.capture());
        assertThat(requestCaptor.getValue().targetServerIds())
                .containsExactlyInAnyOrder(new LinuxServerId("server-a"), new LinuxServerId("server-b"));
        assertThat(operation.targetGeneration()).isEqualTo(1L);
        verify(dispatcher).wake(operation, requestCaptor.getValue().targetServerIds());
    }

    @Test
    void personalMaterializationRequiresCurrentRunningHeartbeatProcessAndTargetsItsServer() {
        OpencodeProcessId processId = new OpencodeProcessId("ocp_1");
        LinuxServerId serverId = new LinuxServerId("server-personal");
        when(processes.findUserBinding(USER_ID, "opencode")).thenReturn(Optional.of(new UserOpencodeProcessBinding(
                USER_ID, "opencode", processId, serverId, 19001, UserOpencodeProcessBindingStatus.ACTIVE,
                NOW.minusSeconds(10), NOW, "trace_binding")));
        when(processes.findOpencodeServerProcessById(processId)).thenReturn(Optional.of(new OpencodeServerProcess(
                processId, USER_ID, serverId, new OpencodeContainerId("opc_1"), 19001, 42L,
                "http://127.0.0.1:19001", OpencodeServerProcessStatus.RUNNING, "/session", "/config",
                NOW.minusSeconds(20), NOW, "ok", NOW.minusSeconds(20), NOW, "trace_process")));
        when(heartbeats.liveOpencodeProcessIds()).thenReturn(Set.of(processId));
        when(registrar.register(any())).thenAnswer(invocation -> {
            AppSourceMaterializationRegistrar.RegistrationRequest request = invocation.getArgument(0);
            return new AppSourceMaterializationRegistrar.RegistrationResult(new AppSourceOperation(
                    request.operationId(), APP_ID, REPOSITORY_ID, null, 1L, USER_ID,
                    AppSourceOperationType.DOWNLOAD, request.requestHash(), AppSourceOperationStatus.PENDING,
                    request.traceId(), NOW, null), request.targetServerIds());
        });

        service.materialize(
                APP_ID.value(), REPOSITORY_ID.value(), command(AppSourcePurpose.PERSONAL), USER_ID, false, "trace_source");

        ArgumentCaptor<AppSourceMaterializationRegistrar.RegistrationRequest> requestCaptor =
                ArgumentCaptor.forClass(AppSourceMaterializationRegistrar.RegistrationRequest.class);
        verify(registrar).register(requestCaptor.capture());
        assertThat(requestCaptor.getValue().targetServerIds()).containsExactly(serverId);
    }

    @Test
    void everyEntryRejectsRevokedMemberBeforeRemoteGitAccess() {
        when(configuration.isActiveMember(APP_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.materialize(
                        APP_ID.value(), REPOSITORY_ID.value(), command(AppSourcePurpose.TEAM),
                        USER_ID, true, "trace_source"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(git, never()).resolveRemoteBranchCommit(any(), any(), any());
        verify(registrar, never()).register(any());
    }

    @Test
    void expiredPersonalOccupationIsReleasedButTeamCannotDowngradeToPersonal() {
        when(heartbeats.liveBackendServerIds()).thenReturn(Set.of(new LinuxServerId("server-a")));
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 4L, null, 5L, "op_old", 1L, NOW.minusSeconds(100), NOW.minusSeconds(10))));
        when(appSources.findActiveSnapshot(REPOSITORY_ID)).thenReturn(Optional.of(snapshot(
                4L, AppSourcePurpose.TEAM, USER_ID, NOW.plusSeconds(100))));

        assertThatThrownBy(() -> service.materialize(
                        APP_ID.value(), REPOSITORY_ID.value(), command(AppSourcePurpose.PERSONAL),
                        USER_ID, true, "trace_source"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        when(appSources.findActiveSnapshot(REPOSITORY_ID)).thenReturn(Optional.of(snapshot(
                4L, AppSourcePurpose.PERSONAL, new UserId("usr_other"), NOW.minusSeconds(1))));
        AppSourceApplicationService.RepositorySummary expired =
                service.listRepositories(APP_ID.value(), USER_ID, false).getFirst();
        assertThat(expired.occupied()).isFalse();
        assertThat(expired.manageable()).isTrue();
        assertThat(expired.downloadState()).isEqualTo(AppSourceApplicationService.DownloadState.DOWNLOADED_EXPIRED);
        assertThat(expired.openable()).isFalse();
        assertThat(expired.unavailableReason()).isEqualTo("SNAPSHOT_EXPIRED");
        when(registrar.register(any())).thenAnswer(invocation -> {
            AppSourceMaterializationRegistrar.RegistrationRequest request = invocation.getArgument(0);
            return new AppSourceMaterializationRegistrar.RegistrationResult(new AppSourceOperation(
                    request.operationId(), APP_ID, REPOSITORY_ID, 4L, 5L, USER_ID,
                    AppSourceOperationType.PROMOTE_TO_TEAM, request.requestHash(), AppSourceOperationStatus.PENDING,
                    request.traceId(), NOW, null), request.targetServerIds());
        });
        AppSourceApplicationService.MaterializationCommand afterExpiry =
                new AppSourceApplicationService.MaterializationCommand(
                        "aso_after_expiry", 4L, "main", COMMIT,
                        List.of(new AppSourceApplicationService.SelectedPathCommand(
                                "src", AppSourcePathType.DIRECTORY)),
                        AppSourcePurpose.TEAM, 2, false);

        assertThat(service.materialize(
                        APP_ID.value(), REPOSITORY_ID.value(), afterExpiry,
                        USER_ID, false, "trace_after_expiry").targetGeneration())
                .isEqualTo(5L);
        verify(registrar).register(any());
    }

    @Test
    void occupiedPersonalSummaryIncludesOwnerDisplayIdentity() {
        UserId ownerId = new UserId("usr_owner");
        when(appSources.findActiveSnapshot(REPOSITORY_ID)).thenReturn(Optional.of(snapshot(
                4L, AppSourcePurpose.PERSONAL, ownerId, NOW.plusSeconds(3600))));
        when(users.findByUserId(ownerId)).thenReturn(Optional.of(new User(
                ownerId, "OWNER001", "源码占用人", "hash", null, null, null,
                UserStatus.ACTIVE, NOW.minusSeconds(100), NOW)));

        AppSourceApplicationService.RepositorySummary summary =
                service.listRepositories(APP_ID.value(), USER_ID, false).getFirst();

        assertThat(summary.occupied()).isTrue();
        assertThat(summary.ownerUserId()).isEqualTo(ownerId);
        assertThat(summary.ownerName()).isEqualTo("源码占用人");
        assertThat(summary.ownerUnifiedAuthId()).isEqualTo("OWNER001");
        assertThat(summary.downloadState())
                .isEqualTo(AppSourceApplicationService.DownloadState.PERSONAL_OCCUPIED);
        assertThat(summary.openable()).isFalse();
        assertThat(summary.manageable()).isFalse();
        assertThat(summary.unavailableReason()).isEqualTo("PERSONAL_OCCUPIED");
    }

    @Test
    void repositorySummaryExposesAvailableSnapshotAndCurrentServerReadiness() {
        AppSourceSnapshot active = snapshot(4L, AppSourcePurpose.TEAM, USER_ID, NOW.plusSeconds(3600));
        when(appSources.findActiveSnapshot(REPOSITORY_ID)).thenReturn(Optional.of(active));
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 4L, null, 5L, "aso_latest", 2L, NOW.minusSeconds(60), NOW)));
        when(appSources.findReplicas(REPOSITORY_ID, 4L)).thenReturn(List.of(
                replica(4L, "server-a", AppSourceReplicaStatus.READY),
                replica(4L, "server-b", AppSourceReplicaStatus.FAILED)));
        AppSourceOperation latest = new AppSourceOperation(
                "aso_latest", APP_ID, REPOSITORY_ID, null, 4L, USER_ID,
                AppSourceOperationType.DOWNLOAD, "request-hash", AppSourceOperationStatus.PARTIAL_FAILED,
                "trace_latest", NOW.minusSeconds(30), NOW.minusSeconds(10));
        when(appSources.findLatestOperation(REPOSITORY_ID)).thenReturn(Optional.of(latest));
        when(appSources.findSteps("aso_latest")).thenReturn(List.of());

        AppSourceApplicationService.RepositorySummary summary =
                service.listRepositories(APP_ID.value(), USER_ID, false, "server-a").getFirst();

        assertThat(summary.downloadState()).isEqualTo(AppSourceApplicationService.DownloadState.DOWNLOADED_ACTIVE);
        assertThat(summary.branch()).isEqualTo("main");
        assertThat(summary.targetCommit()).isEqualTo(COMMIT);
        assertThat(summary.openable()).isTrue();
        assertThat(summary.unavailableReason()).isNull();
        assertThat(summary.latestOperation().operationId()).isEqualTo("aso_latest");
        assertThat(summary.serverSummaries()).extracting(AppSourceApplicationService.ServerSummary::linuxServerId)
                .containsExactly("server-a", "server-b");
    }

    @Test
    void repositoryWithoutSnapshotIsReportedAsNotDownloaded() {
        AppSourceApplicationService.RepositorySummary summary =
                service.listRepositories(APP_ID.value(), USER_ID, false, "server-a").getFirst();

        assertThat(summary.downloadState()).isEqualTo(AppSourceApplicationService.DownloadState.NOT_DOWNLOADED);
        assertThat(summary.generation()).isNull();
        assertThat(summary.occupied()).isFalse();
        assertThat(summary.openable()).isFalse();
        assertThat(summary.manageable()).isTrue();
        assertThat(summary.unavailableReason()).isEqualTo("NOT_DOWNLOADED");
    }

    @Test
    void availableSnapshotIsNotOpenableWithoutAReadyReplicaOnTheCurrentServer() {
        when(appSources.findActiveSnapshot(REPOSITORY_ID)).thenReturn(Optional.of(snapshot(
                4L, AppSourcePurpose.TEAM, USER_ID, NOW.plusSeconds(3600))));
        when(appSources.findReplicas(REPOSITORY_ID, 4L)).thenReturn(List.of(
                replica(4L, "server-b", AppSourceReplicaStatus.READY),
                replica(4L, "server-a", AppSourceReplicaStatus.FAILED)));

        AppSourceApplicationService.RepositorySummary summary =
                service.listRepositories(APP_ID.value(), USER_ID, true, "server-a").getFirst();

        assertThat(summary.downloadState()).isEqualTo(AppSourceApplicationService.DownloadState.DOWNLOADED_ACTIVE);
        assertThat(summary.openable()).isFalse();
        assertThat(summary.manageable()).isTrue();
        assertThat(summary.unavailableReason()).isEqualTo("CURRENT_SERVER_REPLICA_NOT_READY");
    }

    @Test
    void operationSnapshotReturnsPersistedGlobalAndPerServerSafeTimeline() {
        AppSourceOperation operation = new AppSourceOperation(
                "aso_progress", APP_ID, REPOSITORY_ID, null, 4L, USER_ID,
                AppSourceOperationType.DOWNLOAD, "request-hash", AppSourceOperationStatus.RUNNING,
                "trace_progress", NOW.minusSeconds(30), null);
        when(appSources.findOperation("aso_progress")).thenReturn(Optional.of(operation));
        when(appSources.findSnapshot(REPOSITORY_ID, 4L)).thenReturn(Optional.of(snapshot(
                4L, AppSourcePurpose.TEAM, USER_ID, NOW.plusSeconds(3600))));
        when(appSources.findReplicas(REPOSITORY_ID, 4L)).thenReturn(List.of(
                replica(4L, "server-a", AppSourceReplicaStatus.READY)));
        when(appSources.findSteps("aso_progress")).thenReturn(List.of(
                new AppSourceOperationStep(
                        "step-global", "aso_progress", AppSourceStepScope.GLOBAL, null,
                        "DISPATCHING", 1, AppSourceStepStatus.SUCCEEDED, "已登记目标服务器",
                        NOW.minusSeconds(25), NOW.minusSeconds(20), NOW.minusSeconds(20)),
                new AppSourceOperationStep(
                        "step-server", "aso_progress", AppSourceStepScope.SERVER, new LinuxServerId("server-a"),
                        "CHECKOUT", 2, AppSourceStepStatus.RUNNING, "正在检出固定提交",
                        NOW.minusSeconds(10), null, NOW.minusSeconds(4))));

        AppSourceApplicationService.OperationSnapshot snapshot =
                service.getOperation("aso_progress", USER_ID, false);

        assertThat(snapshot.traceId()).isEqualTo("trace_progress");
        assertThat(snapshot.targetCommit()).isEqualTo(COMMIT);
        assertThat(snapshot.globalSteps()).singleElement().satisfies(step -> {
            assertThat(step.safeSummary()).isEqualTo("已登记目标服务器");
            assertThat(step.elapsedMillis()).isEqualTo(5000L);
        });
        assertThat(snapshot.serverSummaries()).singleElement().satisfies(server -> {
            assertThat(server.linuxServerId()).isEqualTo("server-a");
            assertThat(server.targetCommit()).isEqualTo(COMMIT);
            assertThat(server.steps()).singleElement().satisfies(step ->
                    assertThat(step.elapsedMillis()).isEqualTo(6000L));
        });
    }

    @Test
    void teamOperationAllowsMemberOfAnotherCurrentlyLinkedApplication() {
        ApplicationId appB = new ApplicationId("app_2");
        UserId memberB = new UserId("usr_2");
        AppSourceOperation operation = operation("aso_cross_app_team", AppSourceOperationStatus.RUNNING);
        when(appSources.findOperation(operation.operationId())).thenReturn(Optional.of(operation));
        when(configuration.findRepository(REPOSITORY_ID)).thenReturn(Optional.of(repository()));
        when(configuration.findApplicationsByRepository(REPOSITORY_ID)).thenReturn(List.of(
                new ApplicationDefinition(APP_ID, "Billing", true, NOW.minusSeconds(100), NOW),
                new ApplicationDefinition(appB, "Orders", true, NOW.minusSeconds(100), NOW)));
        when(configuration.isActiveMember(APP_ID, memberB)).thenReturn(false);
        when(configuration.isActiveMember(appB, memberB)).thenReturn(true);
        when(appSources.findSnapshot(REPOSITORY_ID, 4L)).thenReturn(Optional.of(snapshot(
                4L, AppSourcePurpose.TEAM, USER_ID, NOW.plusSeconds(3600))));
        when(appSources.findSteps(operation.operationId())).thenReturn(List.of());

        AppSourceApplicationService.OperationSnapshot result =
                service.getOperation(operation.operationId(), memberB, false);

        assertThat(result.operationId()).isEqualTo(operation.operationId());
        assertThat(result.purpose()).isEqualTo(AppSourcePurpose.TEAM);
    }

    @Test
    void teamOperationRechecksUnlinkDisabledApplicationAndMembershipRevocation() {
        ApplicationId appB = new ApplicationId("app_2");
        UserId memberB = new UserId("usr_2");
        ApplicationDefinition enabledB = new ApplicationDefinition(
                appB, "Orders", true, NOW.minusSeconds(100), NOW);
        ApplicationDefinition disabledB = new ApplicationDefinition(
                appB, "Orders", false, NOW.minusSeconds(100), NOW);
        AppSourceOperation operation = operation("aso_realtime_team", AppSourceOperationStatus.RUNNING);
        when(appSources.findOperation(operation.operationId())).thenReturn(Optional.of(operation));
        when(configuration.findApplicationsByRepository(REPOSITORY_ID))
                .thenReturn(List.of(enabledB), List.of(), List.of(disabledB), List.of(enabledB));
        when(configuration.isActiveMember(appB, memberB)).thenReturn(true, false);
        when(appSources.findSnapshot(REPOSITORY_ID, 4L)).thenReturn(Optional.of(snapshot(
                4L, AppSourcePurpose.TEAM, USER_ID, NOW.plusSeconds(3600))));

        assertThat(service.getOperation(operation.operationId(), memberB, false).operationId())
                .isEqualTo(operation.operationId());
        for (String revokedFact : List.of("unlink", "disabled", "membership")) {
            assertThatThrownBy(() -> service.getOperation(operation.operationId(), memberB, false))
                    .as(revokedFact)
                    .isInstanceOfSatisfying(PlatformException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }
    }

    @Test
    void personalOperationAcrossLinkedApplicationAllowsOwnerOrAdminButRejectsOrdinaryMember() {
        ApplicationId appB = new ApplicationId("app_2");
        UserId owner = new UserId("usr_owner");
        UserId admin = new UserId("usr_admin");
        UserId ordinary = new UserId("usr_ordinary");
        AppSourceOperation operation = operation("aso_cross_app_personal", AppSourceOperationStatus.RUNNING);
        when(appSources.findOperation(operation.operationId())).thenReturn(Optional.of(operation));
        when(configuration.findApplicationsByRepository(REPOSITORY_ID)).thenReturn(List.of(
                new ApplicationDefinition(appB, "Orders", true, NOW.minusSeconds(100), NOW)));
        when(configuration.isActiveMember(appB, owner)).thenReturn(true);
        when(configuration.isActiveMember(appB, admin)).thenReturn(true);
        when(configuration.isActiveMember(appB, ordinary)).thenReturn(true);
        when(appSources.findSnapshot(REPOSITORY_ID, 4L)).thenReturn(Optional.of(snapshot(
                4L, AppSourcePurpose.PERSONAL, owner, NOW.plusSeconds(3600))));

        assertThat(service.getOperation(operation.operationId(), owner, false).operationId())
                .isEqualTo(operation.operationId());
        assertThat(service.getOperation(operation.operationId(), admin, true).operationId())
                .isEqualTo(operation.operationId());
        assertThatThrownBy(() -> service.getOperation(operation.operationId(), ordinary, false))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void operationSnapshotRejectsRevokedApplicationMemberEvenWhenTheyAreAppAdmin() {
        AppSourceOperation operation = operation("aso_revoked", AppSourceOperationStatus.RUNNING);
        when(appSources.findOperation("aso_revoked")).thenReturn(Optional.of(operation));
        when(configuration.isActiveMember(APP_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getOperation("aso_revoked", USER_ID, true))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(appSources, never()).findSnapshot(REPOSITORY_ID, 4L);
    }

    @Test
    void personalOperationSnapshotRejectsCurrentMemberWhoIsNeitherOwnerNorAppAdmin() {
        UserId ownerId = new UserId("usr_owner");
        AppSourceOperation operation = operation("aso_personal_denied", AppSourceOperationStatus.RUNNING);
        when(appSources.findOperation("aso_personal_denied")).thenReturn(Optional.of(operation));
        when(appSources.findSnapshot(REPOSITORY_ID, 4L)).thenReturn(Optional.of(snapshot(
                4L, AppSourcePurpose.PERSONAL, ownerId, NOW.plusSeconds(3600))));

        assertThatThrownBy(() -> service.getOperation("aso_personal_denied", USER_ID, false))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(appSources, never()).findSteps("aso_personal_denied");
    }

    @Test
    void personalOperationSnapshotAllowsCurrentApplicationMemberWhoIsAppAdmin() {
        UserId ownerId = new UserId("usr_owner");
        AppSourceOperation operation = operation("aso_personal_admin", AppSourceOperationStatus.SUCCEEDED);
        when(appSources.findOperation("aso_personal_admin")).thenReturn(Optional.of(operation));
        when(appSources.findSnapshot(REPOSITORY_ID, 4L)).thenReturn(Optional.of(snapshot(
                4L, AppSourcePurpose.PERSONAL, ownerId, NOW.plusSeconds(3600))));
        when(appSources.findSteps("aso_personal_admin")).thenReturn(List.of());

        AppSourceApplicationService.OperationSnapshot result =
                service.getOperation("aso_personal_admin", USER_ID, true);

        assertThat(result.operationId()).isEqualTo("aso_personal_admin");
        assertThat(result.purpose()).isEqualTo(AppSourcePurpose.PERSONAL);
    }

    @Test
    void registrationFailureDoesNotDispatchOrStartAnyDiskWork() {
        when(heartbeats.liveBackendServerIds()).thenReturn(Set.of(new LinuxServerId("server-a")));
        when(registrar.register(any())).thenThrow(new IllegalStateException("rollback"));

        assertThatThrownBy(() -> service.materialize(
                        APP_ID.value(), REPOSITORY_ID.value(), command(AppSourcePurpose.TEAM),
                        USER_ID, false, "trace_source"))
                .isInstanceOf(IllegalStateException.class);
        verify(dispatcher, never()).wake(any(), any());
    }

    @Test
    void operationIdIsIdempotentForSamePayloadAndRejectsDifferentPayloadBeforeGit() {
        when(heartbeats.liveBackendServerIds()).thenReturn(Set.of(new LinuxServerId("server-a")));
        ArgumentCaptor<AppSourceMaterializationRegistrar.RegistrationRequest> firstRequest =
                ArgumentCaptor.forClass(AppSourceMaterializationRegistrar.RegistrationRequest.class);
        when(registrar.register(any())).thenAnswer(invocation -> {
            AppSourceMaterializationRegistrar.RegistrationRequest request = invocation.getArgument(0);
            return new AppSourceMaterializationRegistrar.RegistrationResult(new AppSourceOperation(
                    request.operationId(), APP_ID, REPOSITORY_ID, null, 1L, USER_ID,
                    AppSourceOperationType.DOWNLOAD, request.requestHash(), AppSourceOperationStatus.PENDING,
                    request.traceId(), NOW, null), request.targetServerIds());
        });
        AppSourceOperation accepted = service.materialize(
                APP_ID.value(), REPOSITORY_ID.value(), command(AppSourcePurpose.TEAM), USER_ID, false, "trace_source");
        verify(registrar).register(firstRequest.capture());
        when(appSources.findOperation("aso_1")).thenReturn(Optional.of(accepted));
        clearInvocations(git, remote, registrar, dispatcher);

        assertThat(service.materialize(
                APP_ID.value(), REPOSITORY_ID.value(), command(AppSourcePurpose.TEAM), USER_ID, false, "trace_source"))
                .isSameAs(accepted);
        verify(git, never()).resolveRemoteBranchCommit(any(), any(), any());
        verify(registrar, never()).register(any());

        AppSourceApplicationService.MaterializationCommand changedRetention =
                new AppSourceApplicationService.MaterializationCommand(
                        "aso_1", null, "main", COMMIT,
                        List.of(new AppSourceApplicationService.SelectedPathCommand(
                                "src", AppSourcePathType.DIRECTORY)),
                        AppSourcePurpose.TEAM, 3, false);
        assertThatThrownBy(() -> service.materialize(
                        APP_ID.value(), REPOSITORY_ID.value(), changedRetention,
                        USER_ID, false, "trace_source"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(git, never()).resolveRemoteBranchCommit(any(), any(), any());
        verify(registrar, never()).register(any());
    }

    @Test
    void materializationAndRetryCommandsRejectOnlyExactDotSegmentOperationIds() {
        assertThatThrownBy(() -> new AppSourceApplicationService.MaterializationCommand(
                        " \t.\r\n", null, "main", COMMIT,
                        List.of(new AppSourceApplicationService.SelectedPathCommand(
                                "src", AppSourcePathType.DIRECTORY)),
                        AppSourcePurpose.TEAM, 2, false))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> new AppSourceApplicationService.RetryCommand("\u00a0..\u00a0", 4L))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThat(new AppSourceApplicationService.RetryCommand("\u00a0release..1\u00a0", 4L).operationId())
                .isEqualTo("release..1");
    }

    @Test
    void retryKeepsFrozenGenerationAndTargetsOnlyFailedReplicas() {
        AppSourceSnapshot active = snapshot(4L, AppSourcePurpose.TEAM, USER_ID, NOW.plusSeconds(3600));
        when(appSources.findActiveSnapshot(REPOSITORY_ID)).thenReturn(Optional.of(active));
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 4L, null, 5L, "op_old", 3L, NOW.minusSeconds(100), NOW.minusSeconds(10))));
        when(appSources.findReplicas(REPOSITORY_ID, 4L)).thenReturn(List.of(
                replica(4L, "server-a", AppSourceReplicaStatus.FAILED),
                replica(4L, "server-b", AppSourceReplicaStatus.READY)));
        when(retryRegistrar.register(any())).thenAnswer(invocation -> {
            AppSourceReplicaRetryRegistrar.RetryRequest request = invocation.getArgument(0);
            return new AppSourceOperation(
                    request.operationId(), APP_ID, REPOSITORY_ID, 4L, 4L, USER_ID,
                    AppSourceOperationType.RETRY_REPLICAS, request.requestHash(),
                    AppSourceOperationStatus.PENDING, request.traceId(), NOW, null);
        });

        AppSourceOperation retry = service.retry(
                APP_ID.value(), REPOSITORY_ID.value(),
                new AppSourceApplicationService.RetryCommand("aso_retry", 4L),
                USER_ID, false, "trace_retry");

        ArgumentCaptor<AppSourceReplicaRetryRegistrar.RetryRequest> request =
                ArgumentCaptor.forClass(AppSourceReplicaRetryRegistrar.RetryRequest.class);
        verify(retryRegistrar).register(request.capture());
        assertThat(request.getValue().generation()).isEqualTo(4L);
        assertThat(request.getValue().targetServerIds()).containsExactly(new LinuxServerId("server-a"));
        assertThat(active.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        verify(git, never()).resolveRemoteBranchCommit(any(), any(), any());
        verify(remote, never()).listTreeWithCommitOrBranchFallback(any(), any(), any(), any());
        verify(dispatcher).wake(retry, request.getValue().targetServerIds());
    }

    @Test
    void retryReplayReturnsOriginalWhenPendingClaimedOrCompletedWithoutRecomputingTargets() {
        AppSourceSnapshot active = snapshot(4L, AppSourcePurpose.TEAM, USER_ID, NOW.plusSeconds(3600));
        when(appSources.findActiveSnapshot(REPOSITORY_ID)).thenReturn(Optional.of(active));
        when(appSources.findReplicas(REPOSITORY_ID, 4L)).thenReturn(List.of(
                replica(4L, "server-a", AppSourceReplicaStatus.RUNNING)));
        for (AppSourceOperationStatus status : List.of(
                AppSourceOperationStatus.PENDING,
                AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.SUCCEEDED)) {
            String operationId = "aso_retry_" + status.name().toLowerCase(java.util.Locale.ROOT);
            AppSourceOperation original = new AppSourceOperation(
                    operationId, APP_ID, REPOSITORY_ID, 4L, 4L, USER_ID,
                    AppSourceOperationType.RETRY_REPLICAS, "original-request-hash",
                    status, "trace_original", NOW.minusSeconds(30),
                    status.terminal() ? NOW.minusSeconds(10) : null);
            when(appSources.findOperation(operationId)).thenReturn(Optional.of(original));

            AppSourceOperation replay = service.retry(
                    APP_ID.value(), REPOSITORY_ID.value(),
                    new AppSourceApplicationService.RetryCommand(operationId, 4L),
                    USER_ID, false, "trace_replay");

            assertThat(replay).isSameAs(original);
        }
        verify(appSources, never()).findReplicas(REPOSITORY_ID, 4L);
        verify(retryRegistrar, never()).register(any());
        verify(dispatcher, never()).wake(any(), any());
    }

    @Test
    void retryReplayReturnsOriginalAfterGenerationWasReplacedOrSnapshotExpired() {
        AppSourceOperation original = new AppSourceOperation(
                "aso_retry_historic", APP_ID, REPOSITORY_ID, 4L, 4L, USER_ID,
                AppSourceOperationType.RETRY_REPLICAS, "original-request-hash",
                AppSourceOperationStatus.SUCCEEDED, "trace_original",
                NOW.minusSeconds(120), NOW.minusSeconds(60));
        when(appSources.findOperation(original.operationId())).thenReturn(Optional.of(original));
        when(appSources.findActiveSnapshot(REPOSITORY_ID)).thenReturn(Optional.of(snapshot(
                5L, AppSourcePurpose.TEAM, USER_ID, NOW.plusSeconds(3600))));

        AppSourceOperation replay = service.retry(
                APP_ID.value(), REPOSITORY_ID.value(),
                new AppSourceApplicationService.RetryCommand(original.operationId(), 4L),
                USER_ID, false, "trace_replay");

        assertThat(replay).isSameAs(original);
        verify(appSources, never()).findActiveSnapshot(REPOSITORY_ID);
        verify(appSources, never()).findReplicas(any(), anyLong());
    }

    @Test
    void retentionUpdateSynchronizesSnapshotIndexAndEveryPendingCleanupTask() {
        Instant oldExpiry = NOW.plusSeconds(3600);
        Instant acceptedAt = NOW;
        AppSourceSnapshot active = new AppSourceSnapshot(
                REPOSITORY_ID, 4L, "billing-service", AppSourcePurpose.TEAM, USER_ID, "main", COMMIT,
                List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)), "a".repeat(64),
                acceptedAt, oldExpiry, AppSourceSnapshotStatus.ACTIVE, acceptedAt, acceptedAt);
        Instant renewedExpiry = acceptedAt.plusSeconds(120L * 3600L);
        when(appSources.lockRepositoryForAppSource(REPOSITORY_ID)).thenReturn(true);
        when(appSources.findSlotForUpdate(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 4L, null, 5L, "op-old", 2L, NOW.minusSeconds(60), NOW)));
        when(appSources.findSnapshot(REPOSITORY_ID, 4L)).thenReturn(Optional.of(active));
        when(appSources.findCleanupTasksForUpdate(REPOSITORY_ID, 4L)).thenReturn(List.of(
                cleanupTask("cleanup-a", "server-a", oldExpiry, AppSourceCleanupStatus.PENDING),
                cleanupTask("cleanup-b", "server-b", oldExpiry, AppSourceCleanupStatus.RETRY_WAIT)));
        when(appSources.updateActiveSnapshotRetention(
                any(), anyLong(), any(), any(), any(), any())).thenReturn(true);
        when(appSources.rescheduleCleanupTasks(
                any(), anyLong(), any(), any(), any())).thenReturn(2);

        AppSourceApplicationService.RetentionUpdateResult result = service.updateRetention(
                APP_ID.value(), REPOSITORY_ID.value(),
                new AppSourceApplicationService.RetentionUpdateCommand(4L, 120),
                USER_ID, false);

        assertThat(result.retentionHours()).isEqualTo(120);
        assertThat(result.expiresAt()).isEqualTo(renewedExpiry);
        ArgumentCaptor<String> sha = ArgumentCaptor.forClass(String.class);
        verify(appSources).updateActiveSnapshotRetention(
                eq(REPOSITORY_ID), eq(4L), eq(oldExpiry), eq(renewedExpiry), sha.capture(), eq(NOW));
        assertThat(sha.getValue()).matches("[0-9a-f]{64}");
        verify(appSources).rescheduleCleanupTasks(
                REPOSITORY_ID, 4L, oldExpiry, renewedExpiry, NOW);
    }

    @Test
    void retentionUpdateRejectsCleanupThatHasAlreadyStarted() {
        Instant oldExpiry = NOW.plusSeconds(3600);
        AppSourceSnapshot active = snapshot(4L, AppSourcePurpose.TEAM, USER_ID, oldExpiry);
        when(appSources.lockRepositoryForAppSource(REPOSITORY_ID)).thenReturn(true);
        when(appSources.findSlotForUpdate(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 4L, null, 5L, "op-old", 2L, NOW.minusSeconds(60), NOW)));
        when(appSources.findSnapshot(REPOSITORY_ID, 4L)).thenReturn(Optional.of(active));
        when(appSources.findCleanupTasksForUpdate(REPOSITORY_ID, 4L)).thenReturn(List.of(
                cleanupTask("cleanup-a", "server-a", oldExpiry, AppSourceCleanupStatus.RUNNING)));

        assertThatThrownBy(() -> service.updateRetention(
                        APP_ID.value(), REPOSITORY_ID.value(),
                        new AppSourceApplicationService.RetentionUpdateCommand(4L, 24),
                        USER_ID, false))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(appSources, never()).updateActiveSnapshotRetention(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void openRecordsRecentOnlyAfterRealtimeAuthorizationAndReadyWorkspaceResolution() {
        AppSourceSnapshot active = snapshot(4L, AppSourcePurpose.TEAM, USER_ID, NOW.plusSeconds(3600));
        Workspace workspace = new Workspace(
                new WorkspaceId("wrk_source"), "billing-service", "appsource:billing-service",
                WorkspaceStatus.ACTIVE, NOW.minusSeconds(60), NOW, "server-a", "trace_source");
        when(appSources.findSnapshot(REPOSITORY_ID, 4L)).thenReturn(Optional.of(active));
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                REPOSITORY_ID, 4L, null, 5L, "op-old", 2L, NOW.minusSeconds(60), NOW)));
        when(workspaceOpener.open(active, new LinuxServerId("server-a"))).thenReturn(workspace);

        AppSourceApplicationService.OpenResult result = service.open(
                APP_ID.value(), REPOSITORY_ID.value(), 4L, USER_ID, "server-a");

        assertThat(result.workspaceId()).isEqualTo("wrk_source");
        assertThat(result.linuxServerId()).isEqualTo("server-a");
        verify(appSources).upsertRecentSelection(new AppSourceRecentSelection(
                USER_ID, APP_ID, REPOSITORY_ID, 4L, NOW));
    }

    @Test
    void recentSelectionIsDeletedWhenMembershipWasRevoked() {
        when(appSources.findRecentSelection(USER_ID)).thenReturn(Optional.of(
                new AppSourceRecentSelection(USER_ID, APP_ID, REPOSITORY_ID, 4L, NOW.minusSeconds(60))));
        when(configuration.isActiveMember(APP_ID, USER_ID)).thenReturn(false);

        assertThat(service.recent(USER_ID, "server-a")).isEmpty();

        verify(appSources).deleteRecentSelection(USER_ID);
        verify(workspaceOpener, never()).open(any(), any());
    }

    @Test
    void recentSelectionPropagatesInternalFailureWithoutDeletingPreference() {
        when(appSources.findRecentSelection(USER_ID)).thenReturn(Optional.of(
                new AppSourceRecentSelection(USER_ID, APP_ID, REPOSITORY_ID, 4L, NOW.minusSeconds(60))));
        when(configuration.findApplication(APP_ID))
                .thenThrow(new PlatformException(ErrorCode.INTERNAL_ERROR, "configuration unavailable"));

        assertThatThrownBy(() -> service.recent(USER_ID, "server-a"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));

        verify(appSources, never()).deleteRecentSelection(USER_ID);
    }

    @Test
    void clearRecentRefusesRevokedMemberAndKeepsPreference() {
        when(appSources.findRecentSelection(USER_ID)).thenReturn(Optional.of(
                new AppSourceRecentSelection(USER_ID, APP_ID, REPOSITORY_ID, 4L, NOW.minusSeconds(60))));
        when(configuration.isActiveMember(APP_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.clearRecent(USER_ID, "server-a"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(appSources, never()).deleteRecentSelection(USER_ID);
    }

    private AppSourceApplicationService.MaterializationCommand command(AppSourcePurpose purpose) {
        return new AppSourceApplicationService.MaterializationCommand(
                "aso_1", null, "main", COMMIT,
                List.of(new AppSourceApplicationService.SelectedPathCommand("src", AppSourcePathType.DIRECTORY)),
                purpose, 2, false);
    }

    private CodeRepository repository() {
        return new CodeRepository(
                REPOSITORY_ID, "/git/repo.git", "Billing source", "billing-service",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(), false,
                NOW.minusSeconds(100), NOW);
    }

    private AppSourceSnapshot snapshot(
            long generation, AppSourcePurpose purpose, UserId owner, Instant expiresAt) {
        return new AppSourceSnapshot(
                REPOSITORY_ID, generation, "billing-service", purpose, owner, "main", COMMIT,
                List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)), null,
                expiresAt.minusSeconds(3600), expiresAt, AppSourceSnapshotStatus.ACTIVE,
                expiresAt.minusSeconds(3600), expiresAt.minusSeconds(3600));
    }

    private AppSourceReplica replica(long generation, String serverId, AppSourceReplicaStatus status) {
        return new AppSourceReplica(
                REPOSITORY_ID, generation, new LinuxServerId(serverId), null, status,
                null, null, 1, null, null, null, NOW.minusSeconds(60), NOW);
    }

    private AppSourceCleanupTask cleanupTask(
            String cleanupTaskId,
            String serverId,
            Instant deleteAt,
            AppSourceCleanupStatus status) {
        return new AppSourceCleanupTask(
                cleanupTaskId, "aso_download", REPOSITORY_ID, 4L, new LinuxServerId(serverId), deleteAt,
                status, null, null, 0, deleteAt, null, null, "trace_cleanup",
                NOW.minusSeconds(60), NOW);
    }

    private AppSourceOperation operation(
            String operationId,
            AppSourceOperationStatus status) {
        return new AppSourceOperation(
                operationId, APP_ID, REPOSITORY_ID, null, 4L, USER_ID,
                AppSourceOperationType.DOWNLOAD, "request-hash", status,
                "trace_" + operationId, NOW.minusSeconds(30),
                status == AppSourceOperationStatus.SUCCEEDED ? NOW.minusSeconds(10) : null);
    }
}
