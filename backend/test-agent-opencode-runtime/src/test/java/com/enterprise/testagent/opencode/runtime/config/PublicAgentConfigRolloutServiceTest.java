package com.enterprise.testagent.opencode.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PublicAgentConfigRolloutServiceTest {

    private final PublicAgentConfigRolloutRepository repository = mock(PublicAgentConfigRolloutRepository.class);
    private final OpencodeProcessHeartbeatStore heartbeatStore = mock(OpencodeProcessHeartbeatStore.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final AgentRuntimeRegistry registry = mock(AgentRuntimeRegistry.class);
    private final BackendInstanceIdentity backendInstanceIdentity = mock(BackendInstanceIdentity.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PublicAgentConfigRolloutService service;

    @BeforeEach
    void setUp() {
        when(registry.require(AgentRuntimeRegistry.DEFAULT_AGENT_ID)).thenReturn(runtime);
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of());
        when(backendInstanceIdentity.linuxServerId()).thenReturn("linux-1");
        service = new PublicAgentConfigRolloutService(
                repository, heartbeatStore, registry, backendInstanceIdentity, 1000L);
    }

    @Test
    void beginPersistsGateAndLocalServerBeforeBroadcast() {
        when(repository.findActiveRolloutId()).thenReturn(Optional.empty());

        String rolloutId = service.begin("main", "abc123", "linux-1", "trace-1");

        assertThat(rolloutId).startsWith("acr_");
        verify(repository).createRollout(eq(rolloutId), eq("main"), eq("abc123"), eq("trace-1"), any(Instant.class));
        verify(repository).addServer(eq(rolloutId), eq("linux-1"), any(Instant.class));
    }

    @Test
    void runningSessionIsSkippedAndRetryCountIsRecorded() {
        PublicAgentConfigRolloutTarget target = target(2);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(20))).thenReturn(List.of(target));
        when(runtime.runtime(any(AgentRuntimeCommand.class))).thenReturn(Mono.just(new AgentRuntimeResult(
                objectMapper.valueToTree(java.util.Map.of("ses_1", java.util.Map.of("type", "busy"))))));

        service.drainTargets();

        verify(repository).markTargetRetry(eq("act_target"), eq(3), any(Instant.class), eq("SESSION_RUNNING"), any(Instant.class));
        verify(repository, never()).markTargetDisposed(eq("act_target"), any());
    }

    @Test
    void syncedServerSnapshotsItsOwnManagedProcessesBeforeAcknowledgement() {
        ManagerRuntimeSnapshot manager = mock(ManagerRuntimeSnapshot.class);
        OpencodeContainer container = mock(OpencodeContainer.class);
        when(container.linuxServerId()).thenReturn(new LinuxServerId("linux-1"));
        when(container.containerId()).thenReturn(new OpencodeContainerId("container-1"));
        when(manager.container()).thenReturn(container);
        when(manager.managedProcesses()).thenReturn(List.of(new ManagedOpencodeProcessSnapshot(
                4096, 123L, "http://127.0.0.1:4096", null, null, Instant.now(), null, null)));
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of(manager));

        service.markServerSynced("acr_rollout", "linux-1", "trace-1");

        verify(repository).addTarget(any(PublicAgentConfigRolloutTarget.class), any(Instant.class));
        verify(repository).markServerSynced(eq("acr_rollout"), eq("linux-1"), any(Instant.class));
    }

    @Test
    void idleInstanceIsDisposedAndMarkedCompleted() {
        PublicAgentConfigRolloutTarget target = target(0);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(20))).thenReturn(List.of(target));
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.getNodeFactory().booleanNode(true))));

        service.drainTargets();

        verify(repository).markTargetDisposed(eq("act_target"), any(Instant.class));
        verify(repository).completeReadyRollouts(any(Instant.class));
    }

    @Test
    void disposeMustExplicitlyReturnTrueBeforeTargetCompletes() {
        PublicAgentConfigRolloutTarget target = target(0);
        when(repository.claimTargets(eq("linux-1"), any(), any(), eq(20))).thenReturn(List.of(target));
        when(runtime.runtime(any(AgentRuntimeCommand.class)))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())))
                .thenReturn(Mono.just(new AgentRuntimeResult(objectMapper.createObjectNode())));

        service.drainTargets();

        verify(repository).markTargetRetry(
                eq("act_target"), eq(1), any(Instant.class), eq("DISPOSE_REJECTED"), any(Instant.class));
        verify(repository, never()).markTargetDisposed(eq("act_target"), any());
    }

    private PublicAgentConfigRolloutTarget target(int retryCount) {
        return new PublicAgentConfigRolloutTarget(
                "act_target", "acr_rollout", "linux-1", "container-1", 4096,
                "http://127.0.0.1:4096", retryCount, Instant.now().plusSeconds(30));
    }
}
