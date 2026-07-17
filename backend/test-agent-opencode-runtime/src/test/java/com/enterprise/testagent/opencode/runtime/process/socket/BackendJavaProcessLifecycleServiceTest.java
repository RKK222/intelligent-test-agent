package com.enterprise.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

class BackendJavaProcessLifecycleServiceTest {

    @Test
    void heartbeatWritesBuildVersionFromBuildMetadata() {
        FakeRepository repository = new FakeRepository();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        Properties properties = new Properties();
        properties.setProperty("time", "2026-07-15T01:02:03Z");
        BackendJavaProcessLifecycleService service = new BackendJavaProcessLifecycleService(
                repository,
                heartbeatStore,
                new ManagerControlSettings(
                        "secret-token",
                        "http://10.8.0.21:8080",
                        new LinuxServerId("10.8.0.21"),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(5),
                        100),
                Clock.fixed(Instant.parse("2026-06-24T00:00:00Z"), ZoneOffset.UTC),
                new BackendRuntimeMetricsCollector(),
                new BackendBuildVersionProvider(new BuildProperties(properties)));

        service.registerHeartbeat("trace_build_version");

        assertThat(heartbeatStore.backendSnapshots).singleElement().satisfies(snapshot ->
                assertThat(snapshot.buildVersion()).isEqualTo("V20260715.090203"));
    }

    @Test
    void usesProcessStartTimeAsCreatedAtOnFirstHeartbeat() {
        FakeRepository repository = new FakeRepository();
        MutableClock clock = new MutableClock(Instant.parse("2026-06-24T00:00:00Z"));
        BackendJavaProcessLifecycleService service = new BackendJavaProcessLifecycleService(
                repository,
                new RecordingHeartbeatStore(),
                new ManagerControlSettings(
                        "secret-token",
                        "http://10.8.0.21:8080",
                        new LinuxServerId("10.8.0.21"),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(5),
                        100),
                clock);
        clock.advance(Duration.ofMillis(500));

        service.registerHeartbeat("trace_first_heartbeat");

        assertThat(repository.backend.createdAt()).isEqualTo(Instant.parse("2026-06-24T00:00:00Z"));
        assertThat(repository.backend.updatedAt()).isEqualTo(Instant.parse("2026-06-24T00:00:00.500Z"));
    }

    @Test
    void registersReadyBackendProcessAndMarksOffline() {
        FakeRepository repository = new FakeRepository();
        BackendJavaProcessLifecycleService service = new BackendJavaProcessLifecycleService(
                repository,
                new RecordingHeartbeatStore(),
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
    void periodicHeartbeatRefreshesRedisSnapshotWithoutUpdatingDatabaseHeartbeat() {
        FakeRepository repository = new FakeRepository();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        BackendJavaProcessLifecycleService service = new BackendJavaProcessLifecycleService(
                repository,
                heartbeatStore,
                new ManagerControlSettings(
                        "secret-token",
                        "http://10.8.0.21:8080",
                        new LinuxServerId("10.8.0.21"),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(5),
                        100),
                Clock.fixed(Instant.parse("2026-06-24T00:00:00Z"), ZoneOffset.UTC));

        service.registerHeartbeat("trace_first_heartbeat");
        service.registerHeartbeat("trace_second_heartbeat");

        assertThat(repository.savedBackendCount).isEqualTo(1);
        assertThat(repository.savedLinuxServerCount).isEqualTo(1);
        assertThat(heartbeatStore.backendSnapshots).hasSize(2);
        assertThat(heartbeatStore.backendSnapshots.getLast().backendProcess().lastHeartbeatAt())
                .isEqualTo(Instant.parse("2026-06-24T00:00:00Z"));
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
                new RecordingHeartbeatStore(),
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
                new RecordingHeartbeatStore(),
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
        private int savedLinuxServerCount;
        private int savedBackendCount;
        private final List<OpencodeContainerManager> containerManagers = new ArrayList<>();
        private final List<OpencodeManagerBackendConnection> savedConnections = new ArrayList<>();

        @Override public LinuxServer saveLinuxServer(LinuxServer linuxServer) {
            savedLinuxServerCount++;
            this.linuxServer = linuxServer;
            return linuxServer;
        }
        @Override public Optional<LinuxServer> findLinuxServerById(LinuxServerId linuxServerId) { return Optional.ofNullable(linuxServer); }
        @Override public BackendJavaProcess saveBackendJavaProcess(BackendJavaProcess backendJavaProcess) {
            savedBackendCount++;
            this.backend = backendJavaProcess;
            return backendJavaProcess;
        }
        @Override public Optional<BackendJavaProcess> findBackendJavaProcessById(BackendProcessId backendProcessId) { return Optional.ofNullable(backend); }
        @Override public Optional<BackendJavaProcess> findReadyBackendJavaProcessByLinuxServer(LinuxServerId linuxServerId) { return Optional.empty(); }
        @Override public List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit) { return List.of(); }
        @Override public OpencodeContainer saveContainer(OpencodeContainer container) { return container; }
        @Override public Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId) { return Optional.empty(); }
        @Override public List<OpencodeContainer> findHealthyContainers(int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit) { return List.of(); }
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

    private static final class RecordingHeartbeatStore implements OpencodeProcessHeartbeatStore {
        private final List<BackendRuntimeSnapshot> backendSnapshots = new ArrayList<>();

        @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) { }
        @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) { backendSnapshots.add(snapshot); }
        @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) { }
        @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { }
        @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.copyOf(backendSnapshots); }
        @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
        @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
        @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
        @Override public void cleanupExpiredHeartbeats() { }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
