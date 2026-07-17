package com.enterprise.testagent.opencode.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutRepository;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutTarget;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class PublicAgentConfigRolloutServiceTest {

    private final PublicAgentConfigRolloutRepository repository = mock(PublicAgentConfigRolloutRepository.class);
    private final OpencodeProcessHeartbeatStore heartbeatStore = mock(OpencodeProcessHeartbeatStore.class);
    private final OpencodeProcessManagementRepository processRepository = mock(OpencodeProcessManagementRepository.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final AgentRuntimeRegistry registry = mock(AgentRuntimeRegistry.class);
    private final BackendInstanceIdentity backendInstanceIdentity = mock(BackendInstanceIdentity.class);
    private final ManagedWorkspacePathResolver workspacePathResolver = mock(ManagedWorkspacePathResolver.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PublicAgentConfigRolloutService service;

    @BeforeEach
    void setUp() {
        when(registry.require(AgentRuntimeRegistry.DEFAULT_AGENT_ID)).thenReturn(runtime);
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of());
        when(processRepository.findLinuxServers(any(Integer.class))).thenReturn(List.of());
        when(processRepository.findOpencodeServerProcesses(any(Integer.class))).thenReturn(List.of());
        when(repository.findTargetWorkspaceRootPaths("act_target")).thenReturn(List.of("/workspace/a"));
        when(workspacePathResolver.resolve("/workspace/a")).thenReturn(Path.of("/workspace/a"));
        when(backendInstanceIdentity.linuxServerId()).thenReturn("linux-1");
        service = new PublicAgentConfigRolloutService(
                repository,
                heartbeatStore,
                processRepository,
                registry,
                backendInstanceIdentity,
                workspacePathResolver,
                1000L);
    }

    @Test
    void beginPersistsGateAndLocalServerBeforeBroadcast() {
        when(repository.findActiveRolloutId()).thenReturn(Optional.empty());

        String rolloutId = service.begin("main", "abc123", "linux-1", "usr-admin", "trace-1");

        assertThat(rolloutId).startsWith("acr_");
        verify(repository).createRollout(
                eq(rolloutId), eq("main"), eq("abc123"), eq("usr-admin"), eq("trace-1"), any(Instant.class));
        verify(repository).addServer(eq(rolloutId), eq("linux-1"), any(Instant.class));
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

        service.markServerSynced("acr_rollout", "linux-1", "trace-1");

        verify(repository).addTarget(any(PublicAgentConfigRolloutTarget.class), any(Instant.class));
        verify(repository).markServerSynced(eq("acr_rollout"), eq("linux-1"), any(Instant.class));
    }

    @Test
    void serverCannotAcknowledgeSyncUntilItsManagerSnapshotExists() {
        assertThatThrownBy(() -> service.markServerSynced("acr_rollout", "linux-1", "trace-1"))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class);

        verify(repository, never()).markServerSynced(eq("acr_rollout"), eq("linux-1"), any());
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
        verify(runtime, never()).runtime(any());
    }

    private PublicAgentConfigRolloutTarget target(int retryCount) {
        return new PublicAgentConfigRolloutTarget(
                "act_target", "acr_rollout", "usr-1", "linux-1", "container-1", 4096,
                "http://127.0.0.1:4096", retryCount, Instant.now().plusSeconds(60),
                "acl_lease", "trace-rollout");
    }

    private ManagerRuntimeSnapshot managerWithPorts(int... ports) {
        ManagerRuntimeSnapshot manager = mock(ManagerRuntimeSnapshot.class);
        OpencodeContainer container = mock(OpencodeContainer.class);
        when(container.linuxServerId()).thenReturn(new LinuxServerId("linux-1"));
        when(container.containerId()).thenReturn(new OpencodeContainerId("container-1"));
        when(manager.container()).thenReturn(container);
        when(manager.managedProcesses()).thenReturn(java.util.Arrays.stream(ports)
                .mapToObj(port -> new ManagedOpencodeProcessSnapshot(
                        port, 123L, "http://127.0.0.1:" + port, null, null, Instant.now(), null, null))
                .toList());
        return manager;
    }

    private void useManagerPorts(int... ports) {
        ManagerRuntimeSnapshot manager = managerWithPorts(ports);
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of(manager));
    }
}
