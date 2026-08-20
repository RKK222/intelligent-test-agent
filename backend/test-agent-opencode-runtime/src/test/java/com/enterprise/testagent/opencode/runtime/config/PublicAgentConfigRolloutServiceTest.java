package com.enterprise.testagent.opencode.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutRepository;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRuntimeImpactResolver;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutScope;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutWorktreeClaim;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutWorktreePending;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutSyncRequest;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutTarget;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutServerStatus;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutStatus;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigWorktreePending;
import com.enterprise.testagent.domain.notification.UserNotificationType;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessConfigLinkService;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStopRequest;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStopService;
import com.enterprise.testagent.opencode.runtime.process.RuntimeManagementCommandService;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class PublicAgentConfigRolloutServiceTest {

    private static final Instant PROCESS_STARTED_AT = Instant.parse("2026-07-17T12:00:00Z");

    private final PublicAgentConfigRolloutRepository repository = mock(PublicAgentConfigRolloutRepository.class);
    private final OpencodeProcessHeartbeatStore heartbeatStore = mock(OpencodeProcessHeartbeatStore.class);
    private final OpencodeProcessManagementRepository processRepository = mock(OpencodeProcessManagementRepository.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final AgentRuntimeRegistry registry = mock(AgentRuntimeRegistry.class);
    private final BackendInstanceIdentity backendInstanceIdentity = mock(BackendInstanceIdentity.class);
    private final ManagedWorkspacePathResolver workspacePathResolver = mock(ManagedWorkspacePathResolver.class);
    private final UserNotificationApplicationService notificationService = mock(UserNotificationApplicationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PublicAgentConfigRolloutService service;

    @BeforeEach
    void setUp() {
        when(registry.require(AgentRuntimeRegistry.DEFAULT_AGENT_ID)).thenReturn(runtime);
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of());
        when(processRepository.findOpencodeServerProcesses(any(Integer.class))).thenReturn(List.of());
        when(processRepository.findOpencodeServerProcesses(
                any(OpencodeServerProcessFilter.class), any(PageRequest.class)))
                .thenReturn(new PageResponse<>(List.of(), 1, PageRequest.MAX_SIZE, 0));
        when(repository.findTargetWorkspaceRootPaths("act_target")).thenReturn(List.of("/workspace/a"));
        when(workspacePathResolver.resolve("/workspace/a")).thenReturn(Path.of("/workspace/a"));
        when(backendInstanceIdentity.linuxServerId()).thenReturn("linux-1");
        when(repository.renewTargetLease(eq("act_target"), eq("acl_lease"), any(), any())).thenReturn(true);
        when(repository.markTargetDisposed(eq("act_target"), eq("acl_lease"), any())).thenReturn(true);
        when(repository.markTargetRetry(
                eq("act_target"), eq("acl_lease"), any(Integer.class), any(Instant.class),
                any(String.class), any(Instant.class))).thenReturn(true);
        service = new PublicAgentConfigRolloutService(
                repository,
                heartbeatStore,
                processRepository,
                registry,
                backendInstanceIdentity,
                workspacePathResolver,
                1000L);
        service.setNotificationService(notificationService);
    }

    @Test
    void preparePersistsGateAndOnlyRegisteredRolloutMembersBeforeGitMutation() {
        when(repository.findActiveRolloutId(AgentConfigRolloutScope.PUBLIC, null)).thenReturn(Optional.empty());
        when(repository.findActiveServerMembershipIds()).thenReturn(List.of("linux-1", "linux-2"));

        String rolloutId = service.prepare(
                "main", "abc123", "previous123", "linux-1", "usr-admin", "trace-1");

        assertThat(rolloutId).startsWith("acr_");
        verify(repository).createRollout(
                eq(rolloutId), eq(AgentConfigRolloutScope.PUBLIC), isNull(),
                eq("main"), eq("abc123"), eq("previous123"), eq(false), eq("usr-admin"), eq("linux-1"),
                eq("trace-1"), any(Instant.class));
        verify(repository).addServer(eq(rolloutId), eq("linux-1"), any(Instant.class));
        verify(repository).addServer(eq(rolloutId), eq("linux-2"), any(Instant.class));
        verify(repository).findActiveRolloutId(AgentConfigRolloutScope.PUBLIC, null);
    }

    @Test
    void supersedeAtomicallyReplacesDrainingRolloutAndInheritsItsServers() {
        when(repository.findRolloutServerIds("acr_stuck")).thenReturn(List.of("linux-1", "linux-2"));
        when(repository.findActiveServerMembershipIds()).thenReturn(List.of("linux-2", "linux-3"));
        when(repository.supersedePublicRollout(
                eq("acr_stuck"), any(), eq("feature_config"), eq("commit_fixed"), eq("commit_bad"),
                eq(false), eq("usr-admin"), eq("linux-1"), eq("trace-fix"), eq("修复无效 description"),
                any(), any())).thenReturn(true);

        String replacement = service.supersede(
                "acr_stuck",
                "feature_config",
                "commit_fixed",
                "commit_bad",
                false,
                "修复无效 description",
                "linux-1",
                "usr-admin",
                "trace-fix");

        assertThat(replacement).startsWith("acr_").isNotEqualTo("acr_stuck");
        verify(repository).supersedePublicRollout(
                eq("acr_stuck"), eq(replacement), eq("feature_config"), eq("commit_fixed"), eq("commit_bad"),
                eq(false), eq("usr-admin"), eq("linux-1"), eq("trace-fix"), eq("修复无效 description"),
                argThat(serverIds -> serverIds.size() == 3
                        && serverIds.containsAll(List.of("linux-1", "linux-2", "linux-3"))),
                any(Instant.class));
    }

    @Test
    void supersedeRejectsStaleActiveRolloutId() {
        when(repository.findRolloutServerIds("acr_stale")).thenReturn(List.of("linux-1"));
        when(repository.findActiveServerMembershipIds()).thenReturn(List.of("linux-1"));
        when(repository.supersedePublicRollout(
                eq("acr_stale"), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.supersede(
                "acr_stale", "main", "commit_fixed", "commit_bad", false,
                "修复错误配置", "linux-1", "usr-admin", "trace-fix"))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("已变化");
    }

    @Test
    void applicationPreparePersistsVersionScopeBeforeGitMutation() {
        when(repository.findActiveRolloutId(
                AgentConfigRolloutScope.APPLICATION, "awv_1234567890abcdef"))
                .thenReturn(Optional.empty());
        when(repository.findActiveServerMembershipIds()).thenReturn(List.of("linux-1"));

        String rolloutId = service.prepareApplication(
                "awv_1234567890abcdef",
                "feature_testagent_20260718",
                "new123",
                "old123",
                "linux-1",
                "usr-admin",
                "trace-app");

        verify(repository).createRollout(
                eq(rolloutId),
                eq(AgentConfigRolloutScope.APPLICATION),
                eq("awv_1234567890abcdef"),
                eq("feature_testagent_20260718"),
                eq("new123"),
                eq("old123"),
                eq(false),
                eq("usr-admin"),
                eq("linux-1"),
                eq("trace-app"),
                any(Instant.class));
        verify(repository).findActiveRolloutId(
                AgentConfigRolloutScope.APPLICATION, "awv_1234567890abcdef");
    }

    @Test
    void latestPublicRolloutStatusIncludesAllServerErrorsAndDrainCounts() {
        PublicAgentConfigRolloutStatus status = new PublicAgentConfigRolloutStatus(
                "acr_rollout",
                "DRAINING",
                "main",
                "abc123",
                null,
                null,
                null,
                null,
                PROCESS_STARTED_AT,
                PROCESS_STARTED_AT,
                null,
                List.of());
        PublicAgentConfigRolloutServerStatus server = new PublicAgentConfigRolloutServerStatus(
                "linux-2",
                "RETRY_WAIT",
                3,
                2,
                1,
                1,
                0,
                "SESSION_RUNNING",
                null,
                PROCESS_STARTED_AT);
        when(repository.findLatestRolloutStatus(AgentConfigRolloutScope.PUBLIC, null))
                .thenReturn(Optional.of(status));
        when(repository.findRolloutServerStatuses("acr_rollout")).thenReturn(List.of(server));

        PublicAgentConfigRolloutStatus result = service.latestPublicRolloutStatus().orElseThrow();

        assertThat(result.active()).isTrue();
        assertThat(result.servers()).containsExactly(server);
    }

    @Test
    void personalApplicationReloadRegistersOnlyCurrentServerWhenProcessIsRunning() {
        when(processRepository.findUserBinding(new UserId("usr-1"), "opencode"))
                .thenReturn(Optional.of(targetBinding()));
        when(processRepository.findOpencodeServerProcessById(new OpencodeProcessId("ocp_1234567890abcdef")))
                .thenReturn(Optional.of(targetProcess()));
        when(repository.activateRollout(any(), eq("commit_personal"), any(Instant.class))).thenReturn(true);

        Optional<String> rolloutId = service.schedulePersonalApplicationReload(
                "pws_personal",
                "feature_testagent_20260728",
                "commit_personal",
                "linux-1",
                "usr-1",
                "trace-personal");

        assertThat(rolloutId).isPresent();
        verify(repository).createRollout(
                eq(rolloutId.orElseThrow()),
                eq(AgentConfigRolloutScope.PERSONAL_APPLICATION),
                eq("pws_personal"),
                eq("feature_testagent_20260728"),
                eq("commit_personal"),
                eq("commit_personal"),
                eq(false),
                eq("usr-1"),
                eq("linux-1"),
                eq("trace-personal"),
                any(Instant.class));
        verify(repository).addServer(eq(rolloutId.orElseThrow()), eq("linux-1"), any(Instant.class));
        verify(repository).activateRollout(eq(rolloutId.orElseThrow()), eq("commit_personal"), any(Instant.class));
        verify(repository, never()).findActiveServerMembershipIds();
    }

    @Test
    void personalApplicationReloadNeedsNoRolloutWhenProcessIsNotRunning() {
        when(processRepository.findUserBinding(new UserId("usr-1"), "opencode"))
                .thenReturn(Optional.empty());

        Optional<String> rolloutId = service.schedulePersonalApplicationReload(
                "pws_personal",
                "feature_testagent_20260728",
                "commit_personal",
                "linux-1",
                "usr-1",
                "trace-personal");

        assertThat(rolloutId).isEmpty();
        verify(repository, never()).createRollout(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(repository, never()).addServer(any(), any(), any());
    }

    @Test
    void personalApplicationReloadIgnoresInactiveHistoricalBinding() {
        UserOpencodeProcessBinding inactiveBinding = new UserOpencodeProcessBinding(
                new UserId("usr-1"),
                "opencode",
                new OpencodeProcessId("ocp_1234567890abcdef"),
                new LinuxServerId("linux-1"),
                4096,
                UserOpencodeProcessBindingStatus.INACTIVE,
                PROCESS_STARTED_AT,
                PROCESS_STARTED_AT,
                "trace-rollout");
        when(processRepository.findUserBinding(new UserId("usr-1"), "opencode"))
                .thenReturn(Optional.of(inactiveBinding));
        when(processRepository.findOpencodeServerProcessById(new OpencodeProcessId("ocp_1234567890abcdef")))
                .thenReturn(Optional.of(targetProcess()));

        Optional<String> rolloutId = service.schedulePersonalApplicationReload(
                "pws_personal",
                "feature_testagent_20260728",
                "commit_personal",
                "linux-1",
                "usr-1",
                "trace-personal");

        assertThat(rolloutId).isEmpty();
        verify(processRepository, never()).findOpencodeServerProcessById(any());
        verify(repository, never()).createRollout(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void personalApplicationReloadWorkerSnapshotsOnlyInitiatingUser() {
        PublicAgentConfigRolloutSyncRequest request = personalApplicationSyncRequest();
        when(repository.claimPendingSync(
                eq("linux-1"), eq(AgentConfigRolloutScope.PERSONAL_APPLICATION), any(), any()))
                .thenReturn(Optional.of(request));
        when(repository.renewServerSync(eq("acr_personal"), eq("linux-1"), eq("acl_sync"), any(), any()))
                .thenReturn(true);
        when(processRepository.findUserBinding(new UserId("usr-1"), "opencode"))
                .thenReturn(Optional.of(targetBinding()));
        when(processRepository.findOpencodeServerProcessById(new OpencodeProcessId("ocp_1234567890abcdef")))
                .thenReturn(Optional.of(targetProcess()));
        when(processRepository.findOpencodeServerProcesses(
                any(OpencodeServerProcessFilter.class), any(PageRequest.class)))
                .thenReturn(new PageResponse<>(List.of(targetProcess()), 1, PageRequest.MAX_SIZE, 1));
        useManagerPorts(4096);

        service.registerPersonalApplicationReloadTargets();

        ArgumentCaptor<PublicAgentConfigRolloutTarget> targetCaptor =
                ArgumentCaptor.forClass(PublicAgentConfigRolloutTarget.class);
        verify(repository).addTarget(targetCaptor.capture(), any(Instant.class));
        assertThat(targetCaptor.getValue().configScope())
                .isEqualTo(AgentConfigRolloutScope.PERSONAL_APPLICATION);
        assertThat(targetCaptor.getValue().userId()).isEqualTo("usr-1");
        verify(notificationService).syncAgentConfigDispose(
                new UserId("usr-1"), "acr_personal",
                UserNotificationType.AGENT_CONFIG_DISPOSE_PENDING, "trace-personal");
        verify(repository).markServerSynced(eq("acr_personal"), eq("linux-1"), eq("acl_sync"), any());
    }

    @Test
    void personalApplicationReloadWorkerRetriesWhenManagerSnapshotIsUnavailable() {
        PublicAgentConfigRolloutSyncRequest request = personalApplicationSyncRequest();
        when(repository.claimPendingSync(
                eq("linux-1"), eq(AgentConfigRolloutScope.PERSONAL_APPLICATION), any(), any()))
                .thenReturn(Optional.of(request));
        when(repository.renewServerSync(eq("acr_personal"), eq("linux-1"), eq("acl_sync"), any(), any()))
                .thenReturn(true);
        when(processRepository.findUserBinding(new UserId("usr-1"), "opencode"))
                .thenReturn(Optional.of(targetBinding()));
        when(processRepository.findOpencodeServerProcessById(new OpencodeProcessId("ocp_1234567890abcdef")))
                .thenReturn(Optional.of(targetProcess()));

        service.registerPersonalApplicationReloadTargets();

        verify(repository).markServerSyncRetry(
                eq("acr_personal"), eq("linux-1"), eq("acl_sync"), eq(1), any(),
                eq("目标服务器 manager 进程清单尚未就绪"), any());
        verify(repository, never()).markServerSynced(eq("acr_personal"), eq("linux-1"), eq("acl_sync"), any());
    }

    @Test
    void userGateOpensImmediatelyAfterOwnTargetsAreDisposed() {
        when(repository.findBlockingRolloutId("usr-1")).thenReturn(Optional.empty());

        assertThat(service.status(new UserId("usr-1")).allowed()).isTrue();

        verify(repository).findBlockingRolloutId("usr-1");
        verify(repository, never()).findActiveRolloutId();
    }

    @Test
    void userGateRemainsBlockedWhileOwnTargetIsPending() {
        when(repository.findBlockingRolloutId("usr-1")).thenReturn(Optional.of("acr_rollout"));

        assertThat(service.status(new UserId("usr-1")))
                .isEqualTo(com.enterprise.testagent.domain.configuration.PublicAgentConfigMessageGate
                        .MessageGateStatus.blocked("acr_rollout"));
    }

    @Test
    void offlineServerCanBeExplicitlyDecommissionedFromRolloutMembership() {
        when(repository.findPreparing(eq("linux-old"), any())).thenReturn(Optional.empty());
        when(repository.findPendingTargetsByServer("linux-old")).thenReturn(List.of(target(0)));

        service.decommissionServer("linux-old");

        verify(repository).decommissionServerMembership(eq("linux-old"), any(Instant.class));
        verify(notificationService).syncAgentConfigDispose(
                new UserId("usr-1"), "acr_rollout",
                UserNotificationType.AGENT_CONFIG_DISPOSE_SUPERSEDED, "trace-rollout");
        verify(repository).completeReadyRollouts(any(Instant.class));
    }

    @Test
    void currentServerCannotBeDecommissionedWhileItIsOnline() {
        when(repository.findPreparing(eq("linux-1"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decommissionServer("linux-1"))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class);

        verify(repository, never()).decommissionServerMembership(eq("linux-1"), any());
    }

    @Test
    void runningSessionIsSkippedAndRetryCountIsRecorded() {
        PublicAgentConfigRolloutTarget target = target(2);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class))).thenReturn(Mono.just(new AgentRuntimeResult(
                objectMapper.valueToTree(java.util.Map.of("ses_1", java.util.Map.of("type", "busy"))))));

        service.drainTargets();

        verify(repository).markTargetRetry(
                eq("act_target"), eq("acl_lease"), eq(3), any(Instant.class), eq("SESSION_RUNNING"), any(Instant.class));
        verify(repository, never()).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
        verify(notificationService).syncAgentConfigDispose(
                new UserId("usr-1"), "acr_rollout",
                UserNotificationType.AGENT_CONFIG_DISPOSE_PENDING, "trace-rollout");
    }

    @Test
    void lateWorkerCannotOverwriteDisposeNotificationAfterLosingRetryLease() {
        PublicAgentConfigRolloutTarget target = target(0);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        when(repository.markTargetRetry(
                eq("act_target"), eq("acl_lease"), eq(1), any(Instant.class),
                eq("SESSION_RUNNING"), any(Instant.class))).thenReturn(false);
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class))).thenReturn(Mono.just(new AgentRuntimeResult(
                objectMapper.valueToTree(java.util.Map.of("ses_1", java.util.Map.of("type", "busy"))))));

        service.drainTargets();

        verify(repository).markTargetRetry(
                eq("act_target"), eq("acl_lease"), eq(1), any(Instant.class),
                eq("SESSION_RUNNING"), any(Instant.class));
        verify(notificationService, never()).syncAgentConfigDispose(any(), any(), any(), any());
    }

    @Test
    void everyBoundWorkspaceMustBeIdleBeforeGlobalDispose() {
        PublicAgentConfigRolloutTarget target = target(0);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        when(repository.findTargetWorkspaceRootPaths("act_target"))
                .thenReturn(List.of("/workspace/a", "/workspace/b"));
        when(workspacePathResolver.resolve("/workspace/b")).thenReturn(Path.of("/workspace/b"));
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.valueToTree(
                        java.util.Map.of("ses_1", java.util.Map.of("type", "idle"))))))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.valueToTree(
                        java.util.Map.of("ses_2", java.util.Map.of("type", "busy"))))));

        service.drainTargets();

        ArgumentCaptor<AgentRuntimeCommand> commandCaptor = ArgumentCaptor.forClass(AgentRuntimeCommand.class);
        verify(runtime, times(2)).runtime(commandCaptor.capture());
        assertThat(commandCaptor.getAllValues())
                .extracting(AgentRuntimeCommand::path, AgentRuntimeCommand::directory, AgentRuntimeCommand::traceId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("/session/status", "/workspace/a", "trace-rollout"),
                        org.assertj.core.groups.Tuple.tuple("/session/status", "/workspace/b", "trace-rollout"));
        verify(repository).markTargetRetry(
                eq("act_target"), eq("acl_lease"), eq(1), any(), eq("SESSION_RUNNING"), any());
        verify(repository, never()).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
    }

    @Test
    void syncedServerSnapshotsItsOwnManagedProcessesBeforeAcknowledgement() {
        ManagerRuntimeSnapshot manager = managerWithPorts(4096);
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of(manager));

        PublicAgentConfigRolloutSyncRequest request = syncRequest();
        when(repository.renewServerSync(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any(), any()))
                .thenReturn(true);

        service.markServerSynced(request);

        verify(processRepository).findOpencodeServerProcesses(
                new OpencodeServerProcessFilter(null, new LinuxServerId("linux-1"), null, null),
                new PageRequest(1, PageRequest.MAX_SIZE));
        verify(repository).addTarget(any(PublicAgentConfigRolloutTarget.class), any(Instant.class));
        verify(repository).markServerSynced(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any(Instant.class));
    }

    @Test
    void serverCannotAcknowledgeSyncUntilItsManagerSnapshotExists() {
        PublicAgentConfigRolloutSyncRequest request = syncRequest();
        when(repository.renewServerSync(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.markServerSynced(request))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class);

        verify(repository, never()).markServerSynced(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any());
    }

    @Test
    void applicationSyncSnapshotsOnlyUsersWhosePersonalWorktreesWereUpdated() {
        ManagerRuntimeSnapshot manager = managerWithPorts(4096, 4097);
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of(manager));
        OpencodeServerProcess included = mock(OpencodeServerProcess.class);
        when(included.userId()).thenReturn(new UserId("usr-included"));
        when(included.linuxServerId()).thenReturn(new LinuxServerId("linux-1"));
        when(included.containerId()).thenReturn(new OpencodeContainerId("container-1"));
        when(included.port()).thenReturn(4096);
        when(included.pid()).thenReturn(123L);
        when(included.startedAt()).thenReturn(PROCESS_STARTED_AT);
        OpencodeServerProcess excluded = mock(OpencodeServerProcess.class);
        when(excluded.userId()).thenReturn(new UserId("usr-excluded"));
        when(excluded.linuxServerId()).thenReturn(new LinuxServerId("linux-1"));
        when(excluded.containerId()).thenReturn(new OpencodeContainerId("container-1"));
        when(excluded.port()).thenReturn(4097);
        when(excluded.pid()).thenReturn(123L);
        when(excluded.startedAt()).thenReturn(PROCESS_STARTED_AT);
        when(processRepository.findOpencodeServerProcesses(
                any(OpencodeServerProcessFilter.class), any(PageRequest.class)))
                .thenReturn(new PageResponse<>(List.of(included, excluded), 1, PageRequest.MAX_SIZE, 2));
        PublicAgentConfigRolloutSyncRequest request = applicationSyncRequest();
        when(repository.renewServerSync(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any(), any()))
                .thenReturn(true);

        service.markServerSyncedForUsers(request, Set.of("usr-included"));

        ArgumentCaptor<PublicAgentConfigRolloutTarget> targetCaptor =
                ArgumentCaptor.forClass(PublicAgentConfigRolloutTarget.class);
        verify(repository).addTarget(targetCaptor.capture(), any(Instant.class));
        assertThat(targetCaptor.getValue().userId()).isEqualTo("usr-included");
        assertThat(targetCaptor.getValue().port()).isEqualTo(4096);
        verify(repository).markServerSynced(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any());
    }

    @Test
    void applicationSyncConvergesLegacyMillisecondStartTimeSkewUsingManagerIdentity() {
        ManagerRuntimeSnapshot manager = managerWithPorts(4096);
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of(manager));
        OpencodeServerProcess legacy = mock(OpencodeServerProcess.class);
        when(legacy.userId()).thenReturn(new UserId("usr-included"));
        when(legacy.linuxServerId()).thenReturn(new LinuxServerId("linux-1"));
        when(legacy.containerId()).thenReturn(new OpencodeContainerId("container-1"));
        when(legacy.port()).thenReturn(4096);
        when(legacy.pid()).thenReturn(123L);
        when(legacy.startedAt()).thenReturn(PROCESS_STARTED_AT.plusNanos(2_803_824));
        when(processRepository.findOpencodeServerProcesses(
                any(OpencodeServerProcessFilter.class), any(PageRequest.class)))
                .thenReturn(new PageResponse<>(List.of(legacy), 1, PageRequest.MAX_SIZE, 1));
        PublicAgentConfigRolloutSyncRequest request = applicationSyncRequest();
        when(repository.renewServerSync(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any(), any()))
                .thenReturn(true);

        service.markServerSyncedForUsers(request, Set.of("usr-included"));

        ArgumentCaptor<PublicAgentConfigRolloutTarget> targetCaptor =
                ArgumentCaptor.forClass(PublicAgentConfigRolloutTarget.class);
        verify(repository).addTarget(targetCaptor.capture(), any(Instant.class));
        assertThat(targetCaptor.getValue()).satisfies(target -> {
            assertThat(target.userId()).isEqualTo("usr-included");
            assertThat(target.processPid()).isEqualTo(123L);
            assertThat(target.processStartedAt()).isEqualTo(PROCESS_STARTED_AT);
        });
        verify(repository).markServerSynced(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any());
    }

    @Test
    void applicationSyncPersistsPendingWorktreeAndCompletesServerWithoutManagerSnapshot() {
        PublicAgentConfigRolloutSyncRequest request = applicationSyncRequest();
        when(repository.renewServerSync(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any(), any()))
                .thenReturn(true);
        List<AgentConfigRolloutWorktreePending> pending = List.of(
                new AgentConfigRolloutWorktreePending("pws_pending", "usr-pending", "LOCAL_CHANGES"));

        service.markServerSyncedForUsers(request, Set.of(), pending);

        verify(repository).savePendingApplicationWorktrees(
                eq("acr_rollout"),
                eq("linux-1"),
                eq(request.commitHash()),
                eq(request.traceId()),
                eq(pending),
                any(Instant.class));
        verify(repository, never()).addTarget(any(), any());
        verify(repository).markServerSynced(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any());
    }

    @Test
    void publicSyncPersistsPendingWorktreeButStillSnapshotsTheSharedRuntimeServer() {
        PublicAgentConfigRolloutSyncRequest request = syncRequest();
        useManagerPorts();
        when(repository.renewServerSync(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any(), any()))
                .thenReturn(true);
        List<PublicAgentConfigWorktreePending> pending = List.of(
                new PublicAgentConfigWorktreePending("agw_public", "usr-pending", "LOCAL_CHANGES"));

        service.markPublicServerSynced(request, pending);

        verify(repository).savePendingPublicWorktrees(
                eq("acr_rollout"),
                eq("linux-1"),
                eq(request.commitHash()),
                eq(request.traceId()),
                eq(pending),
                any(Instant.class));
        verify(repository).markServerSynced(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any());
    }

    @Test
    void applicationSyncRetriesWhenEligibleUsersStartTimeSkewExceedsCompatibilityWindow() {
        ManagerRuntimeSnapshot manager = managerWithPorts(4096);
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of(manager));
        OpencodeServerProcess stale = mock(OpencodeServerProcess.class);
        when(stale.userId()).thenReturn(new UserId("usr-included"));
        when(stale.linuxServerId()).thenReturn(new LinuxServerId("linux-1"));
        when(stale.containerId()).thenReturn(new OpencodeContainerId("container-1"));
        when(stale.port()).thenReturn(4096);
        when(stale.pid()).thenReturn(123L);
        when(stale.startedAt()).thenReturn(PROCESS_STARTED_AT.minusSeconds(2));
        when(processRepository.findOpencodeServerProcesses(
                any(OpencodeServerProcessFilter.class), any(PageRequest.class)))
                .thenReturn(new PageResponse<>(List.of(stale), 1, PageRequest.MAX_SIZE, 1));
        PublicAgentConfigRolloutSyncRequest request = applicationSyncRequest();
        when(repository.renewServerSync(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.markServerSyncedForUsers(request, Set.of("usr-included")))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("身份尚未收敛");

        verify(repository, never()).addTarget(any(), any());
        verify(repository, never()).markServerSynced(eq("acr_rollout"), eq("linux-1"), eq("acl_sync"), any());
    }

    @Test
    void synchronizedLateWorktreeDoesNotRegisterDisposeBeforeUsersOtherWorktreesFinish() {
        AgentConfigRolloutWorktreeClaim claim = new AgentConfigRolloutWorktreeClaim(
                "acr_rollout",
                "awv_version",
                "pws_personal",
                "usr-included",
                "linux-1",
                "abc123",
                "trace-1",
                3,
                Instant.now().plusSeconds(180),
                "acl_worktree");
        when(repository.markApplicationWorktreeSynchronized(eq(claim), any())).thenReturn(true);
        when(repository.hasIncompleteApplicationWorktrees(
                "acr_rollout", "linux-1", "usr-included"))
                .thenReturn(true);

        service.markApplicationWorktreeSynchronized(claim);

        verify(repository, never()).addTarget(any(), any());
    }

    @Test
    void idleInstanceIsDisposedAndMarkedCompleted() {
        PublicAgentConfigRolloutTarget target = target(0);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.getNodeFactory().booleanNode(true))));

        service.drainTargets();

        verify(repository).markTargetDisposed(eq("act_target"), eq("acl_lease"), any(Instant.class));
        verify(notificationService).syncAgentConfigDispose(
                new UserId("usr-1"), "acr_rollout",
                UserNotificationType.AGENT_CONFIG_DISPOSE_SUCCEEDED, "trace-rollout");
        verify(repository).completeReadyRollouts(any(Instant.class));
    }

    @Test
    void disposeMustExplicitlyReturnTrueBeforeTargetCompletes() {
        PublicAgentConfigRolloutTarget target = target(0);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())));

        service.drainTargets();

        verify(repository).markTargetRetry(
                eq("act_target"), eq("acl_lease"), eq(1), any(Instant.class), eq("DISPOSE_REJECTED"), any(Instant.class));
        verify(repository, never()).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
        verify(notificationService).syncAgentConfigDispose(
                new UserId("usr-1"), "acr_rollout",
                UserNotificationType.AGENT_CONFIG_DISPOSE_FAILED, "trace-rollout");
    }

    @Test
    void publicRolloutRestoresSharedConfigLinkBeforeGlobalDispose() {
        PublicAgentConfigRolloutTarget target = target(0, AgentConfigRolloutScope.PUBLIC);
        OpencodeProcessConfigLinkService configLinkService = mock(OpencodeProcessConfigLinkService.class);
        service.setConfigLinkService(configLinkService);
        OpencodeServerProcess process = targetProcess();
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        when(processRepository.findUserBinding(new UserId("usr-1"), "opencode"))
                .thenReturn(Optional.of(targetBinding()));
        when(processRepository.findOpencodeServerProcessById(process.processId())).thenReturn(Optional.of(process));
        when(configLinkService.isSharedConfigPath(process.configPath())).thenReturn(false);
        when(configLinkService.isManagedConfigPath(process.sessionPath(), process.configPath())).thenReturn(true);
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.getNodeFactory().booleanNode(true))));

        service.drainTargets();

        verify(configLinkService).switchToShared(process.sessionPath(), process.configPath());
        verify(repository).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
    }

    @Test
    void publicToolModuleRolloutRestartsTrackedProcessInsteadOfUsingGlobalDispose() {
        PublicAgentConfigRolloutTarget target = new PublicAgentConfigRolloutTarget(
                "act_target", "acr_rollout", AgentConfigRolloutScope.PUBLIC,
                "usr-1", "linux-1", "container-1", 4096,
                123L, PROCESS_STARTED_AT, "http://127.0.0.1:4096", 0,
                Instant.now().plusSeconds(60), "acl_lease", "trace-rollout", false,
                "commit-old", "commit-new");
        PublicAgentConfigRuntimeImpactResolver impactResolver = mock(PublicAgentConfigRuntimeImpactResolver.class);
        RuntimeManagementCommandService commandService = mock(RuntimeManagementCommandService.class);
        OpencodeProcessConfigLinkService configLinkService = mock(OpencodeProcessConfigLinkService.class);
        OpencodeServerProcess process = targetProcess();
        OpencodeServerProcess replacement = replacementProcess(process.configPath());
        service.setRuntimeImpactResolver(impactResolver);
        service.setRuntimeManagementCommandService(commandService);
        service.setConfigLinkService(configLinkService);
        when(impactResolver.requiresProcessRestart(
                AgentConfigRolloutScope.PUBLIC, null, "commit-old", "commit-new")).thenReturn(true);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        when(processRepository.findUserBinding(new UserId("usr-1"), "opencode"))
                .thenReturn(Optional.of(targetBinding()));
        when(processRepository.findOpencodeServerProcessById(process.processId()))
                .thenReturn(Optional.of(process), Optional.of(process), Optional.of(replacement));
        when(configLinkService.isSharedConfigPath(process.configPath())).thenReturn(false);
        when(configLinkService.isManagedConfigPath(process.sessionPath(), process.configPath())).thenReturn(true);
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())))
                .thenReturn(Mono.just(new AgentRuntimeResult(
                        objectMapper.createArrayNode().add("bash").add("workspace-git"))));

        service.drainTargets();

        verify(configLinkService).switchToShared(process.sessionPath(), process.configPath());
        verify(commandService).restartTrackedProcess(process, "trace-rollout", true);
        verify(runtime, times(2)).runtime(any(AgentRuntimeCommand.class));
        verify(repository).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
    }

    @Test
    void applicationToolModuleRolloutRestartsWithoutChangingPublicConfigLink() {
        PublicAgentConfigRolloutTarget target = new PublicAgentConfigRolloutTarget(
                "act_target", "acr_rollout", AgentConfigRolloutScope.APPLICATION,
                "usr-1", "linux-1", "container-1", 4096,
                123L, PROCESS_STARTED_AT, "http://127.0.0.1:4096", 0,
                Instant.now().plusSeconds(60), "acl_lease", "trace-rollout", false,
                "commit-old", "commit-new", "awv_1");
        PublicAgentConfigRuntimeImpactResolver impactResolver = mock(PublicAgentConfigRuntimeImpactResolver.class);
        RuntimeManagementCommandService commandService = mock(RuntimeManagementCommandService.class);
        OpencodeProcessConfigLinkService configLinkService = mock(OpencodeProcessConfigLinkService.class);
        OpencodeServerProcess process = targetProcess();
        OpencodeServerProcess replacement = replacementProcess(process.configPath());
        service.setRuntimeImpactResolver(impactResolver);
        service.setRuntimeManagementCommandService(commandService);
        service.setConfigLinkService(configLinkService);
        when(impactResolver.requiresProcessRestart(
                AgentConfigRolloutScope.APPLICATION, "awv_1", "commit-old", "commit-new")).thenReturn(true);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        when(processRepository.findUserBinding(new UserId("usr-1"), "opencode"))
                .thenReturn(Optional.of(targetBinding()));
        when(processRepository.findOpencodeServerProcessById(process.processId()))
                .thenReturn(Optional.of(process), Optional.of(replacement));
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())))
                .thenReturn(Mono.just(new AgentRuntimeResult(
                        objectMapper.createArrayNode().add("bash").add("bank-query"))));

        service.drainTargets();

        verify(configLinkService, never()).switchToShared(any(), any());
        verify(commandService).restartTrackedProcess(process, "trace-rollout", false);
        verify(repository).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
    }

    @Test
    void toolCatalogProbeFailureRemainsRetryableForScheduledCompensation() {
        PublicAgentConfigRolloutTarget target = new PublicAgentConfigRolloutTarget(
                "act_target", "acr_rollout", AgentConfigRolloutScope.APPLICATION,
                "usr-1", "linux-1", "container-1", 4096,
                123L, PROCESS_STARTED_AT, "http://127.0.0.1:4096", 0,
                Instant.now().plusSeconds(60), "acl_lease", "trace-rollout", false,
                "commit-old", "commit-new", "awv_1");
        PublicAgentConfigRuntimeImpactResolver impactResolver = mock(PublicAgentConfigRuntimeImpactResolver.class);
        RuntimeManagementCommandService commandService = mock(RuntimeManagementCommandService.class);
        OpencodeServerProcess process = targetProcess();
        OpencodeServerProcess replacement = replacementProcess(process.configPath());
        service.setRuntimeImpactResolver(impactResolver);
        service.setRuntimeManagementCommandService(commandService);
        when(impactResolver.requiresProcessRestart(
                AgentConfigRolloutScope.APPLICATION, "awv_1", "commit-old", "commit-new")).thenReturn(true);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        when(processRepository.findUserBinding(new UserId("usr-1"), "opencode"))
                .thenReturn(Optional.of(targetBinding()));
        when(processRepository.findOpencodeServerProcessById(process.processId()))
                .thenReturn(Optional.of(process), Optional.of(replacement));
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createArrayNode())));

        service.drainTargets();

        verify(commandService).restartTrackedProcess(process, "trace-rollout", false);
        verify(repository).markTargetRetry(
                eq("act_target"), eq("acl_lease"), eq(1), any(Instant.class),
                eq("TOOL_CATALOG_INVALID"), any(Instant.class));
        verify(repository, never()).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
    }

    @Test
    void failedTargetDoesNotBlockFollowingTargetInSameDrainBatch() {
        PublicAgentConfigRolloutTarget failed = new PublicAgentConfigRolloutTarget(
                "act_failed", "acr_rollout", AgentConfigRolloutScope.PUBLIC,
                "usr-failed", "linux-1", "container-1", 4096,
                null, null, "http://127.0.0.1:4096", 0,
                Instant.now().plusSeconds(60), "acl_failed", "trace-failed");
        PublicAgentConfigRolloutTarget healthy = new PublicAgentConfigRolloutTarget(
                "act_healthy", "acr_rollout", AgentConfigRolloutScope.PUBLIC,
                "usr-healthy", "linux-1", "container-1", 4097,
                456L, PROCESS_STARTED_AT, "http://127.0.0.1:4097", 0,
                Instant.now().plusSeconds(60), "acl_healthy", "trace-healthy");
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1)))
                .thenReturn(List.of(failed, healthy));
        when(repository.renewTargetLease(eq("act_failed"), eq("acl_failed"), any(), any())).thenReturn(true);
        when(repository.renewTargetLease(eq("act_healthy"), eq("acl_healthy"), any(), any())).thenReturn(true);
        when(repository.markTargetRetry(
                eq("act_failed"), eq("acl_failed"), any(Integer.class), any(Instant.class),
                any(String.class), any(Instant.class))).thenReturn(true);
        when(repository.markTargetDisposed(eq("act_healthy"), eq("acl_healthy"), any())).thenReturn(true);
        useManagerPorts();

        service.drainTargets();

        verify(repository).markTargetRetry(
                eq("act_failed"), eq("acl_failed"), eq(1), any(Instant.class),
                eq("TARGET_PROCESS_IDENTITY_MISSING"), any(Instant.class));
        verify(repository).markTargetDisposed(eq("act_healthy"), eq("acl_healthy"), any());
    }

    @Test
    void toolRestartRecoveryWaitsWhenReplacementProcessHasBusySession() {
        PublicAgentConfigRolloutTarget target = new PublicAgentConfigRolloutTarget(
                "act_target", "acr_rollout", AgentConfigRolloutScope.PUBLIC,
                "usr-1", "linux-1", "container-1", 4096,
                123L, PROCESS_STARTED_AT, "http://127.0.0.1:4096", 0,
                Instant.now().plusSeconds(60), "acl_lease", "trace-rollout", false,
                "commit-old", "commit-new");
        PublicAgentConfigRuntimeImpactResolver impactResolver = mock(PublicAgentConfigRuntimeImpactResolver.class);
        RuntimeManagementCommandService commandService = mock(RuntimeManagementCommandService.class);
        OpencodeProcessConfigLinkService configLinkService = mock(OpencodeProcessConfigLinkService.class);
        Instant replacementStartedAt = PROCESS_STARTED_AT.plusSeconds(60);
        OpencodeServerProcess replacement = new OpencodeServerProcess(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                new UserId("usr-1"),
                new LinuxServerId("linux-1"),
                new OpencodeContainerId("container-1"),
                4096,
                456L,
                "http://127.0.0.1:4096",
                OpencodeServerProcessStatus.RUNNING,
                "/session/usr-1",
                "/session/usr-1/.testagent-runtime/personal-preview/config",
                replacementStartedAt,
                replacementStartedAt,
                "healthy",
                replacementStartedAt,
                replacementStartedAt,
                "trace-replacement");
        service.setRuntimeImpactResolver(impactResolver);
        service.setRuntimeManagementCommandService(commandService);
        service.setConfigLinkService(configLinkService);
        when(impactResolver.requiresProcessRestart(
                AgentConfigRolloutScope.PUBLIC, null, "commit-old", "commit-new")).thenReturn(true);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        when(processRepository.findUserBinding(new UserId("usr-1"), "opencode"))
                .thenReturn(Optional.of(targetBinding()));
        when(processRepository.findOpencodeServerProcessById(replacement.processId()))
                .thenReturn(Optional.of(replacement));
        when(configLinkService.isLinkedToShared(replacement.sessionPath(), replacement.configPath())).thenReturn(false);
        useManagerPorts();
        when(runtime.runtime(any(AgentRuntimeCommand.class))).thenReturn(Mono.just(new AgentRuntimeResult(
                objectMapper.valueToTree(java.util.Map.of("ses_1", java.util.Map.of("type", "busy"))))));

        service.drainTargets();

        verify(repository).markTargetRetry(
                eq("act_target"), eq("acl_lease"), eq(1), any(Instant.class),
                eq("SESSION_RUNNING"), any(Instant.class));
        verify(commandService, never()).restartTrackedProcess(any(), any(), anyBoolean());
        verify(configLinkService, never()).switchToShared(any(), any());
        verify(repository, never()).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
    }

    @Test
    void supersedingRolloutForceStopsOnlyFlaggedExactProcessWithoutSessionStatus() {
        PublicAgentConfigRolloutTarget target = new PublicAgentConfigRolloutTarget(
                "act_target", "acr_replacement", AgentConfigRolloutScope.PUBLIC,
                "usr-1", "linux-1", "container-1", 4096,
                123L, PROCESS_STARTED_AT, "http://127.0.0.1:4096", 0,
                Instant.now().plusSeconds(60), "acl_lease", "trace-rollout", true);
        OpencodeProcessStopService stopService = mock(OpencodeProcessStopService.class);
        service.setStopService(stopService);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        when(processRepository.findUserBinding(new UserId("usr-1"), "opencode"))
                .thenReturn(Optional.of(targetBinding()));
        when(processRepository.findOpencodeServerProcessById(new OpencodeProcessId("ocp_1234567890abcdef")))
                .thenReturn(Optional.of(targetProcess()));
        useManagerPorts(4096);

        service.drainTargets();

        verify(stopService).stopAndVerify(any(OpencodeProcessStopRequest.class));
        verify(runtime, never()).runtime(any(AgentRuntimeCommand.class));
        verify(repository).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
    }

    @Test
    void publicRolloutWithUnknownUserRestoresSharedConfigFromExactManagerSnapshot() {
        PublicAgentConfigRolloutTarget target = target(null, 0, AgentConfigRolloutScope.PUBLIC);
        OpencodeProcessConfigLinkService configLinkService = mock(OpencodeProcessConfigLinkService.class);
        service.setConfigLinkService(configLinkService);
        String sessionPath = "/session/unknown";
        String configPath = "/session/unknown/.testagent-runtime/current-public-config";
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        when(configLinkService.isSharedConfigPath(configPath)).thenReturn(false);
        when(configLinkService.isManagedConfigPath(sessionPath, configPath)).thenReturn(true);
        useManagerProcess(4096, 123L, PROCESS_STARTED_AT, sessionPath, configPath);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.getNodeFactory().booleanNode(true))));

        service.drainTargets();

        verify(configLinkService).switchToShared(sessionPath, configPath);
        verify(processRepository, never()).findUserBinding(any(UserId.class), eq("opencode"));
        verify(repository).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
    }

    @Test
    void publicRolloutWithUnknownUserAndMissingManagerPathsRemainsFailClosed() {
        PublicAgentConfigRolloutTarget target = target(null, 0, AgentConfigRolloutScope.PUBLIC);
        OpencodeProcessConfigLinkService configLinkService = mock(OpencodeProcessConfigLinkService.class);
        service.setConfigLinkService(configLinkService);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())));

        service.drainTargets();

        verify(processRepository, never()).findUserBinding(any(UserId.class), eq("opencode"));
        verify(repository).markTargetRetry(
                eq("act_target"), eq("acl_lease"), eq(1), any(),
                eq("PROCESS_CONFIG_IDENTITY_CHANGED"), any());
        verify(repository, never()).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
    }

    @Test
    void applicationRolloutDisposesConvergedUserWithoutChangingPublicConfigLink() {
        PublicAgentConfigRolloutTarget target = target(0, AgentConfigRolloutScope.APPLICATION);
        OpencodeProcessConfigLinkService configLinkService = mock(OpencodeProcessConfigLinkService.class);
        service.setConfigLinkService(configLinkService);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.getNodeFactory().booleanNode(true))));

        service.drainTargets();

        verify(configLinkService, never()).switchToShared(any(), any());
        verify(repository).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
    }

    @Test
    void malformedSessionStatusFailsClosedWithoutDispose() {
        PublicAgentConfigRolloutTarget target = target(0);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        useManagerPorts(4096);
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.getNodeFactory().textNode("unknown"))));

        service.drainTargets();

        verify(repository).markTargetRetry(
                eq("act_target"), eq("acl_lease"), eq(1), any(), eq("SESSION_STATUS_INVALID"), any());
        verify(runtime).runtime(any(AgentRuntimeCommand.class));
    }

    @Test
    void missingProcessIsCompletedByLocalManagerWithoutCallingRemoteDispose() {
        PublicAgentConfigRolloutTarget target = target(0);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        useManagerPorts();

        service.drainTargets();

        verify(repository).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
        verify(notificationService).syncAgentConfigDispose(
                new UserId("usr-1"), "acr_rollout",
                UserNotificationType.AGENT_CONFIG_DISPOSE_SUCCEEDED, "trace-rollout");
        verify(runtime, never()).runtime(any());
    }

    @Test
    void reusedPortWithDifferentProcessIdentityNeverDisposesReplacement() {
        PublicAgentConfigRolloutTarget target = target(0);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        useManagerProcess(4096, 999L, PROCESS_STARTED_AT.plusSeconds(1));

        service.drainTargets();

        verify(repository).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
        verify(runtime, never()).runtime(any());
    }

    @Test
    void legacyTargetWithoutProcessIdentityFailsClosed() {
        PublicAgentConfigRolloutTarget target = new PublicAgentConfigRolloutTarget(
                "act_target", "acr_rollout", AgentConfigRolloutScope.PUBLIC,
                "usr-1", "linux-1", "container-1", 4096,
                null, null, "http://127.0.0.1:4096", 0, Instant.now().plusSeconds(60),
                "acl_lease", "trace-rollout");
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(1))).thenReturn(List.of(target));
        useManagerPorts(4096);

        service.drainTargets();

        verify(repository).markTargetRetry(
                eq("act_target"), eq("acl_lease"), eq(1), any(),
                eq("TARGET_PROCESS_IDENTITY_MISSING"), any());
        verify(repository, never()).markTargetDisposed(eq("act_target"), eq("acl_lease"), any());
        verify(runtime, never()).runtime(any());
    }

    private PublicAgentConfigRolloutTarget target(int retryCount) {
        return target(retryCount, AgentConfigRolloutScope.PUBLIC);
    }

    private PublicAgentConfigRolloutTarget target(int retryCount, AgentConfigRolloutScope scope) {
        return target("usr-1", retryCount, scope);
    }

    private PublicAgentConfigRolloutTarget target(
            String userId,
            int retryCount,
            AgentConfigRolloutScope scope) {
        return new PublicAgentConfigRolloutTarget(
                "act_target", "acr_rollout", scope,
                userId, "linux-1", "container-1", 4096,
                123L, PROCESS_STARTED_AT, "http://127.0.0.1:4096", retryCount, Instant.now().plusSeconds(60),
                "acl_lease", "trace-rollout");
    }

    private OpencodeServerProcess targetProcess() {
        return new OpencodeServerProcess(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                new UserId("usr-1"),
                new LinuxServerId("linux-1"),
                new OpencodeContainerId("container-1"),
                4096,
                123L,
                "http://127.0.0.1:4096",
                OpencodeServerProcessStatus.RUNNING,
                "/session/usr-1",
                "/session/usr-1/.testagent-runtime/current-public-config",
                PROCESS_STARTED_AT,
                PROCESS_STARTED_AT,
                "healthy",
                PROCESS_STARTED_AT,
                PROCESS_STARTED_AT,
                "trace-rollout");
    }

    private OpencodeServerProcess replacementProcess(String configPath) {
        Instant replacementStartedAt = PROCESS_STARTED_AT.plusSeconds(60);
        return new OpencodeServerProcess(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                new UserId("usr-1"),
                new LinuxServerId("linux-1"),
                new OpencodeContainerId("container-1"),
                4096,
                456L,
                "http://127.0.0.1:4096",
                OpencodeServerProcessStatus.RUNNING,
                "/session/usr-1",
                configPath,
                replacementStartedAt,
                replacementStartedAt,
                "healthy",
                replacementStartedAt,
                replacementStartedAt,
                "trace-replacement");
    }

    private UserOpencodeProcessBinding targetBinding() {
        return new UserOpencodeProcessBinding(
                new UserId("usr-1"),
                "opencode",
                new OpencodeProcessId("ocp_1234567890abcdef"),
                new LinuxServerId("linux-1"),
                4096,
                UserOpencodeProcessBindingStatus.ACTIVE,
                PROCESS_STARTED_AT,
                PROCESS_STARTED_AT,
                "trace-rollout");
    }

    private PublicAgentConfigRolloutSyncRequest syncRequest() {
        return new PublicAgentConfigRolloutSyncRequest(
                "acr_rollout", "main", "abc123", "usr-admin", "trace-1",
                0, Instant.now().plusSeconds(180), "acl_sync");
    }

    private PublicAgentConfigRolloutSyncRequest applicationSyncRequest() {
        return new PublicAgentConfigRolloutSyncRequest(
                "acr_rollout",
                AgentConfigRolloutScope.APPLICATION,
                "awv_version",
                "main",
                "abc123",
                "usr-admin",
                "trace-1",
                0,
                Instant.now().plusSeconds(180),
                "acl_sync");
    }

    private PublicAgentConfigRolloutSyncRequest personalApplicationSyncRequest() {
        return new PublicAgentConfigRolloutSyncRequest(
                "acr_personal",
                AgentConfigRolloutScope.PERSONAL_APPLICATION,
                "pws_personal",
                "feature_testagent_20260728",
                "commit_personal",
                "usr-1",
                "trace-personal",
                0,
                Instant.now().plusSeconds(180),
                "acl_sync");
    }

    private ManagerRuntimeSnapshot managerWithPorts(int... ports) {
        ManagerRuntimeSnapshot manager = mock(ManagerRuntimeSnapshot.class);
        OpencodeContainer container = mock(OpencodeContainer.class);
        when(container.linuxServerId()).thenReturn(new LinuxServerId("linux-1"));
        when(container.containerId()).thenReturn(new OpencodeContainerId("container-1"));
        when(manager.container()).thenReturn(container);
        when(manager.managedProcesses()).thenReturn(java.util.Arrays.stream(ports)
                .mapToObj(port -> new ManagedOpencodeProcessSnapshot(
                        port, 123L, "http://127.0.0.1:" + port, null, null, PROCESS_STARTED_AT, null, null))
                .toList());
        return manager;
    }

    private void useManagerPorts(int... ports) {
        ManagerRuntimeSnapshot manager = managerWithPorts(ports);
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of(manager));
    }

    private void useManagerProcess(int port, long pid, Instant startedAt) {
        useManagerProcess(port, pid, startedAt, null, null);
    }

    private void useManagerProcess(
            int port,
            long pid,
            Instant startedAt,
            String sessionPath,
            String configPath) {
        ManagerRuntimeSnapshot manager = mock(ManagerRuntimeSnapshot.class);
        OpencodeContainer container = mock(OpencodeContainer.class);
        when(container.linuxServerId()).thenReturn(new LinuxServerId("linux-1"));
        when(container.containerId()).thenReturn(new OpencodeContainerId("container-1"));
        when(manager.container()).thenReturn(container);
        when(manager.managedProcesses()).thenReturn(List.of(new ManagedOpencodeProcessSnapshot(
                port, pid, "http://127.0.0.1:" + port,
                sessionPath, configPath, startedAt, null, null)));
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of(manager));
    }
}
