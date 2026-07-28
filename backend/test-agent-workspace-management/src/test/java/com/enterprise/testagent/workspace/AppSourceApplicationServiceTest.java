package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
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
        when(appSources.findSlot(REPOSITORY_ID)).thenReturn(Optional.empty());
        when(appSources.findActiveSnapshot(REPOSITORY_ID)).thenReturn(Optional.empty());
        when(git.resolveRemoteBranchCommit("/git/repo.git", "main", null)).thenReturn(COMMIT);
        when(remote.listTree("/git/repo.git", COMMIT, null)).thenReturn(List.of(
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
                Clock.fixed(NOW, ZoneOffset.UTC));
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
        assertThat(service.listRepositories(APP_ID.value(), USER_ID, false).getFirst().occupied())
                .isFalse();
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
        verify(remote, never()).listTree(any(), any(), any());
        verify(dispatcher).wake(retry, request.getValue().targetServerIds());
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
}
