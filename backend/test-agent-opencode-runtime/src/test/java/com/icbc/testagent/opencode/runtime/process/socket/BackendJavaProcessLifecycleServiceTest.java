package com.icbc.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BackendJavaProcessLifecycleServiceTest {

    @Test
    void registersReadyBackendProcessAndMarksOffline() {
        FakeRepository repository = new FakeRepository();
        BackendJavaProcessLifecycleService service = new BackendJavaProcessLifecycleService(
                repository,
                new ManagerControlSettings(
                        "secret-token",
                        "http://10.8.0.21:8080",
                        new LinuxServerId("10.8.0.21"),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(5),
                        100),
                Clock.fixed(Instant.parse("2026-06-24T00:00:00Z"), ZoneOffset.UTC));

        service.registerHeartbeat("trace_1234567890abcdef");

        assertThat(repository.linuxServer.status()).isEqualTo(LinuxServerStatus.READY);
        assertThat(repository.backend.status()).isEqualTo(BackendJavaProcessStatus.READY);
        assertThat(repository.backend.listenUrl()).isEqualTo("http://10.8.0.21:8080");

        service.markOffline("trace_1234567890abcdef");

        assertThat(repository.backend.status()).isEqualTo(BackendJavaProcessStatus.OFFLINE);
    }

    @Test
    void heartbeatBootstrapsLocalManagerBackendConnectionWhenMissing() {
        FakeRepository repository = new FakeRepository();
        // 模拟 V17 在数据库预置的同服务器 CONNECTED manager
        repository.containerManagers.add(new OpencodeContainerManager(
                new ContainerManagerId("mgr_local_4096"),
                new OpencodeContainerId("ctr_local_4096"),
                new LinuxServerId("127.0.0.1"),
                "opencode-manager.v1",
                ManagerConnectionStatus.CONNECTED,
                Map.of(),
                Instant.parse("2026-06-24T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"),
                "trace_seed_local_opencode_machine"));
        BackendJavaProcessLifecycleService service = new BackendJavaProcessLifecycleService(
                repository,
                new ManagerControlSettings(
                        "secret-token",
                        "http://127.0.0.1:8080",
                        new LinuxServerId("127.0.0.1"),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(5),
                        100),
                Clock.fixed(Instant.parse("2026-06-24T00:00:00Z"), ZoneOffset.UTC));

        service.registerHeartbeat("trace_heartbeat_bootstrap");

        assertThat(repository.savedConnections)
                .anySatisfy(connection -> {
                    assertThat(connection.managerId().value()).isEqualTo("mgr_local_4096");
                    assertThat(connection.backendProcessId().value())
                            .isEqualTo(service.backendProcessId().value());
                    assertThat(connection.status()).isEqualTo(ManagerConnectionStatus.CONNECTED);
                });
    }

    @Test
    void heartbeatLeavesManagersOnOtherServersUntouched() {
        FakeRepository repository = new FakeRepository();
        // 模拟同表里另一个 Linux 服务器上的 manager
        repository.containerManagers.add(new OpencodeContainerManager(
                new ContainerManagerId("mgr_other_host"),
                new OpencodeContainerId("ctr_other"),
                new LinuxServerId("10.8.0.21"),
                "opencode-manager.v1",
                ManagerConnectionStatus.CONNECTED,
                Map.of(),
                Instant.parse("2026-06-24T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"),
                Instant.parse("2026-06-24T00:00:00Z"),
                "trace_seed_other"));
        BackendJavaProcessLifecycleService service = new BackendJavaProcessLifecycleService(
                repository,
                new ManagerControlSettings(
                        "secret-token",
                        "http://127.0.0.1:8080",
                        new LinuxServerId("127.0.0.1"),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(5),
                        100),
                Clock.fixed(Instant.parse("2026-06-24T00:00:00Z"), ZoneOffset.UTC));

        service.registerHeartbeat("trace_heartbeat_skip_other");

        assertThat(repository.savedConnections).isEmpty();
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository {
        private LinuxServer linuxServer;
        private BackendJavaProcess backend;
        private final List<OpencodeContainerManager> containerManagers = new ArrayList<>();
        private final List<OpencodeManagerBackendConnection> savedConnections = new ArrayList<>();

        @Override public LinuxServer saveLinuxServer(LinuxServer linuxServer) { this.linuxServer = linuxServer; return linuxServer; }
        @Override public Optional<LinuxServer> findLinuxServerById(LinuxServerId linuxServerId) { return Optional.ofNullable(linuxServer); }
        @Override public BackendJavaProcess saveBackendJavaProcess(BackendJavaProcess backendJavaProcess) { this.backend = backendJavaProcess; return backendJavaProcess; }
        @Override public Optional<BackendJavaProcess> findBackendJavaProcessById(BackendProcessId backendProcessId) { return Optional.ofNullable(backend); }
        @Override public List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit) { return List.of(); }
        @Override public OpencodeContainer saveContainer(OpencodeContainer container) { return container; }
        @Override public Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId) { return Optional.empty(); }
        @Override public List<OpencodeContainer> findHealthyContainers(int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackend(BackendProcessId backendProcessId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackendByLinuxServer(BackendProcessId backendProcessId, LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public OpencodeContainerManager saveContainerManager(OpencodeContainerManager manager) { return manager; }
        @Override public Optional<OpencodeContainerManager> findContainerManagerById(ContainerManagerId managerId) { return Optional.empty(); }
        @Override public OpencodeManagerBackendConnection saveManagerBackendConnection(OpencodeManagerBackendConnection connection) {
            savedConnections.add(connection);
            return connection;
        }
        @Override public Optional<OpencodeManagerBackendConnection> findManagerBackendConnection(ContainerManagerId managerId, BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) { return process; }
        @Override public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) { return Optional.empty(); }
        @Override public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) { return List.of(); }
        @Override public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) { return binding; }
        @Override public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) { return Optional.empty(); }
        @Override public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return List.of(); }
        @Override public List<OpencodeContainerManager> findContainerManagers(int limit) { return new ArrayList<>(containerManagers); }
    }
}
