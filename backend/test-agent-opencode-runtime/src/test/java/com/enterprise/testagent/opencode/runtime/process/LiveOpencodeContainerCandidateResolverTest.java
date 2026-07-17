package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerConnectionRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LiveOpencodeContainerCandidateResolverTest {

    private static final Instant NOW = Instant.parse("2026-07-15T02:00:00Z");
    private static final BackendProcessId CURRENT_BACKEND = new BackendProcessId("bjp_current");

    private OpencodeProcessHeartbeatStore heartbeatStore;
    private ManagerConnectionRegistry connectionRegistry;
    private LiveOpencodeContainerCandidateResolver resolver;

    @BeforeEach
    void setUp() {
        heartbeatStore = mock(OpencodeProcessHeartbeatStore.class);
        BackendJavaProcessLifecycleService backendLifecycle = mock(BackendJavaProcessLifecycleService.class);
        when(backendLifecycle.backendProcessId()).thenReturn(CURRENT_BACKEND);
        connectionRegistry = new ManagerConnectionRegistry();
        resolver = new LiveOpencodeContainerCandidateResolver(heartbeatStore, backendLifecycle, connectionRegistry);
    }

    @Test
    void filtersByRealtimeStateBackendCapacityAndLocalConnectionThenSortsByLoadAndId() {
        ManagerRuntimeSnapshot validB = snapshot("ctr_b", "server-a", 2, 4, OpencodeContainerStatus.READY,
                ManagerConnectionStatus.CONNECTED, CURRENT_BACKEND, ManagerConnectionStatus.CONNECTED, NOW);
        ManagerRuntimeSnapshot validA = snapshot("ctr_a", "server-b", 1, 4, OpencodeContainerStatus.READY,
                ManagerConnectionStatus.CONNECTED, CURRENT_BACKEND, ManagerConnectionStatus.CONNECTED, NOW);
        ManagerRuntimeSnapshot foreignBackend = snapshot("ctr_foreign", "server-a", 0, 4, OpencodeContainerStatus.READY,
                ManagerConnectionStatus.CONNECTED, new BackendProcessId("bjp_other"), ManagerConnectionStatus.CONNECTED, NOW);
        ManagerRuntimeSnapshot managerDisconnected = snapshot("ctr_manager_down", "server-a", 0, 4,
                OpencodeContainerStatus.READY, ManagerConnectionStatus.DISCONNECTED, CURRENT_BACKEND,
                ManagerConnectionStatus.CONNECTED, NOW);
        ManagerRuntimeSnapshot containerBusy = snapshot("ctr_busy", "server-a", 0, 4,
                OpencodeContainerStatus.BUSY, ManagerConnectionStatus.CONNECTED, CURRENT_BACKEND,
                ManagerConnectionStatus.CONNECTED, NOW);
        ManagerRuntimeSnapshot full = snapshot("ctr_full", "server-a", 4, 4, OpencodeContainerStatus.READY,
                ManagerConnectionStatus.CONNECTED, CURRENT_BACKEND, ManagerConnectionStatus.CONNECTED, NOW);
        ManagerRuntimeSnapshot noLocalSocket = snapshot("ctr_no_socket", "server-a", 0, 4,
                OpencodeContainerStatus.READY, ManagerConnectionStatus.CONNECTED, CURRENT_BACKEND,
                ManagerConnectionStatus.CONNECTED, NOW);
        ManagerRuntimeSnapshot duplicateOldValid = snapshot("ctr_duplicate", "server-a", 0, 4,
                OpencodeContainerStatus.READY, ManagerConnectionStatus.CONNECTED, CURRENT_BACKEND,
                ManagerConnectionStatus.CONNECTED, NOW.minusSeconds(1));
        ManagerRuntimeSnapshot duplicateLatestDown = snapshot("ctr_duplicate", "server-a", 0, 4,
                OpencodeContainerStatus.READY, ManagerConnectionStatus.DISCONNECTED, CURRENT_BACKEND,
                ManagerConnectionStatus.CONNECTED, NOW);
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of(
                validB, foreignBackend, managerDisconnected, containerBusy, full, noLocalSocket,
                duplicateOldValid, validA, duplicateLatestDown));
        connect(validA.container().containerId());
        connect(validB.container().containerId());
        connect(foreignBackend.container().containerId());
        connect(managerDisconnected.container().containerId());
        connect(containerBusy.container().containerId());
        connect(full.container().containerId());
        connect(duplicateOldValid.container().containerId());

        List<OpencodeContainer> candidates = resolver.findCandidates(100);

        assertThat(candidates).extracting(container -> container.containerId().value())
                .containsExactly("ctr_a", "ctr_b");
    }

    @Test
    void limitsRebuildCandidatesToOriginalServerAndAppliesLimitAfterSorting() {
        ManagerRuntimeSnapshot otherServer = snapshot("ctr_other", "server-b", 0, 4,
                OpencodeContainerStatus.READY, ManagerConnectionStatus.CONNECTED, CURRENT_BACKEND,
                ManagerConnectionStatus.CONNECTED, NOW);
        ManagerRuntimeSnapshot heavier = snapshot("ctr_z", "server-a", 2, 4,
                OpencodeContainerStatus.READY, ManagerConnectionStatus.CONNECTED, CURRENT_BACKEND,
                ManagerConnectionStatus.CONNECTED, NOW);
        ManagerRuntimeSnapshot lighter = snapshot("ctr_y", "server-a", 1, 4,
                OpencodeContainerStatus.READY, ManagerConnectionStatus.CONNECTED, CURRENT_BACKEND,
                ManagerConnectionStatus.CONNECTED, NOW);
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of(otherServer, heavier, lighter));
        connect(otherServer.container().containerId());
        connect(heavier.container().containerId());
        connect(lighter.container().containerId());

        List<OpencodeContainer> candidates = resolver.findCandidates(new LinuxServerId("server-a"), 1);

        assertThat(candidates).extracting(container -> container.containerId().value())
                .containsExactly("ctr_y");
    }

    @Test
    void returnsNoCandidatesWhenRedisHasNoLiveSnapshots() {
        when(heartbeatStore.liveManagerSnapshots()).thenReturn(List.of());

        assertThat(resolver.findCandidates(100)).isEmpty();
    }

    @Test
    void mapsRedisFailureToRuntimeStateUnavailableWithoutFallback() {
        when(heartbeatStore.liveManagerSnapshots()).thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> resolver.findCandidates(100))
                .isInstanceOf(PlatformException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.RUNTIME_STATE_UNAVAILABLE);
    }

    private void connect(OpencodeContainerId containerId) {
        connectionRegistry.register(
                new ContainerManagerId("mgr_" + containerId.value()),
                containerId,
                CURRENT_BACKEND,
                message -> { });
    }

    private static ManagerRuntimeSnapshot snapshot(
            String containerId,
            String linuxServerId,
            int currentProcesses,
            int maxProcesses,
            OpencodeContainerStatus containerStatus,
            ManagerConnectionStatus managerStatus,
            BackendProcessId connectedBackend,
            ManagerConnectionStatus backendConnectionStatus,
            Instant heartbeatAt) {
        OpencodeContainerId typedContainerId = new OpencodeContainerId(containerId);
        LinuxServerId typedServerId = new LinuxServerId(linuxServerId);
        ContainerManagerId managerId = new ContainerManagerId("mgr_" + containerId);
        OpencodeContainer container = new OpencodeContainer(
                typedContainerId,
                typedServerId,
                containerId,
                4096,
                4100,
                maxProcesses,
                currentProcesses,
                containerStatus,
                heartbeatAt,
                NOW.minusSeconds(30),
                heartbeatAt,
                "trace_candidate");
        OpencodeContainerManager manager = new OpencodeContainerManager(
                managerId,
                typedContainerId,
                typedServerId,
                "1.0",
                managerStatus,
                Map.of(),
                heartbeatAt,
                NOW.minusSeconds(30),
                heartbeatAt,
                "trace_candidate");
        OpencodeManagerBackendConnection connection = new OpencodeManagerBackendConnection(
                managerId,
                connectedBackend,
                backendConnectionStatus,
                NOW.minusSeconds(30),
                heartbeatAt,
                heartbeatAt,
                "trace_candidate");
        return new ManagerRuntimeSnapshot(container, manager, List.of(connection));
    }
}
