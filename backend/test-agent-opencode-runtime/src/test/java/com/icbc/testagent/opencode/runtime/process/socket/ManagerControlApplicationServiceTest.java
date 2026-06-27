package com.icbc.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManagerControlApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-24T00:00:00Z");

    @Test
    void managerHeartbeatWritesRedisSnapshotWithoutUpdatingDatabaseHeartbeat() {
        FakeRepository repository = new FakeRepository();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        BackendJavaProcessLifecycleService backendLifecycle = backendLifecycle(repository, heartbeatStore);
        ManagerControlApplicationService service = new ManagerControlApplicationService(
                repository,
                heartbeatStore,
                backendLifecycle,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ManagerControlMessage heartbeat = ManagerControlMessage.managerHeartbeat(
                "mgr_1234567890abcdef",
                "ctr_01",
                "10.8.0.12",
                "opencode-a",
                4096,
                4100,
                5,
                2,
                Map.of("commands", List.of("start", "health")),
                List.of("bjp_1234567890abcdef", "bjp_2234567890abcdef"),
                "trace_1234567890abcdef");

        service.managerHeartbeat(heartbeat);

        assertThat(repository.savedContainers).isEmpty();
        assertThat(repository.savedManagers).isEmpty();
        assertThat(repository.savedConnections).isEmpty();
        assertThat(heartbeatStore.managerSnapshots).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.container().currentProcesses()).isEqualTo(2);
            assertThat(snapshot.manager().managerId().value()).isEqualTo("mgr_1234567890abcdef");
            assertThat(snapshot.connections()).extracting(connection -> connection.backendProcessId().value())
                    .containsExactly("bjp_1234567890abcdef", "bjp_2234567890abcdef");
        });
    }

    @Test
    void backendListResponseReadsLiveBackendSnapshotsFromRedis() {
        FakeRepository repository = new FakeRepository();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        BackendJavaProcessLifecycleService backendLifecycle = backendLifecycle(repository, heartbeatStore);
        ManagerControlApplicationService service = new ManagerControlApplicationService(
                repository,
                heartbeatStore,
                backendLifecycle,
                Clock.fixed(NOW, ZoneOffset.UTC));
        heartbeatStore.backendSnapshots.add(new BackendRuntimeSnapshot(
                new LinuxServer(
                        new LinuxServerId("10.8.0.12"),
                        "10.8.0.12",
                        LinuxServerStatus.READY,
                        Map.of(),
                        NOW,
                        NOW,
                        NOW,
                        "trace_backend"),
                new BackendJavaProcess(
                        new BackendProcessId("bjp_1234567890abcdef"),
                        new LinuxServerId("10.8.0.12"),
                        "http://10.8.0.12:8080",
                        BackendJavaProcessStatus.READY,
                        NOW,
                        NOW,
                        NOW,
                        NOW,
                        "trace_backend")));

        ManagerControlMessage response = service.backendListResponse("trace_1234567890abcdef");

        assertThat(response.type()).isEqualTo(ManagerControlProtocol.TYPE_BACKEND_LIST_RESPONSE);
        assertThat(response.backendEndpoints()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.backendProcessId()).isEqualTo("bjp_1234567890abcdef");
            assertThat(endpoint.webSocketUrl()).isEqualTo("ws://10.8.0.12:8080/api/internal/platform/opencode-runtime/manager/ws");
        });
    }

    private static BackendJavaProcessLifecycleService backendLifecycle(
            FakeRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        return new BackendJavaProcessLifecycleService(
                repository,
                heartbeatStore,
                new ManagerControlSettings(
                        "secret-token",
                        "http://10.8.0.21:8080",
                        new LinuxServerId("10.8.0.21"),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(1),
                        100),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class RecordingHeartbeatStore implements OpencodeProcessHeartbeatStore {
        private final List<BackendRuntimeSnapshot> backendSnapshots = new ArrayList<>();
        private final List<ManagerRuntimeSnapshot> managerSnapshots = new ArrayList<>();

        @Override public void recordBackendHeartbeat(BackendProcessId backendProcessId, Instant heartbeatAt) { }
        @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) { backendSnapshots.add(snapshot); }
        @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) { managerSnapshots.add(snapshot); }
        @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { }
        @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.copyOf(backendSnapshots); }
        @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.copyOf(managerSnapshots); }
        @Override public Set<BackendProcessId> liveBackendProcessIds() { return Set.of(); }
        @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
        @Override public void cleanupExpiredHeartbeats() { }
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository {
        private final List<OpencodeContainer> savedContainers = new ArrayList<>();
        private final List<OpencodeContainerManager> savedManagers = new ArrayList<>();
        private final List<OpencodeManagerBackendConnection> savedConnections = new ArrayList<>();

        @Override public LinuxServer saveLinuxServer(LinuxServer linuxServer) { return linuxServer; }
        @Override public Optional<LinuxServer> findLinuxServerById(LinuxServerId linuxServerId) { return Optional.empty(); }
        @Override public BackendJavaProcess saveBackendJavaProcess(BackendJavaProcess backendJavaProcess) { return backendJavaProcess; }
        @Override public Optional<BackendJavaProcess> findBackendJavaProcessById(BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit) { return List.of(); }
        @Override public OpencodeContainer saveContainer(OpencodeContainer container) { savedContainers.add(container); return container; }
        @Override public Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId) { return Optional.empty(); }
        @Override public List<OpencodeContainer> findHealthyContainers(int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackend(BackendProcessId backendProcessId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackendByLinuxServer(BackendProcessId backendProcessId, LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public OpencodeContainerManager saveContainerManager(OpencodeContainerManager manager) { savedManagers.add(manager); return manager; }
        @Override public Optional<OpencodeContainerManager> findContainerManagerById(ContainerManagerId managerId) { return Optional.empty(); }
        @Override public OpencodeManagerBackendConnection saveManagerBackendConnection(OpencodeManagerBackendConnection connection) { savedConnections.add(connection); return connection; }
        @Override public Optional<OpencodeManagerBackendConnection> findManagerBackendConnection(ContainerManagerId managerId, BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) { return process; }
        @Override public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) { return Optional.empty(); }
        @Override public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) { return List.of(); }
        @Override public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) { return binding; }
        @Override public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) { return Optional.empty(); }
        @Override public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return List.of(); }
        @Override public PageResponse<OpencodeServerProcess> findOpencodeServerProcesses(OpencodeServerProcessFilter filter, PageRequest pageRequest) {
            return new PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
        }
    }
}
