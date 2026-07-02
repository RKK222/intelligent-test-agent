package com.icbc.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpencodeProcessStatusQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void missingProcessReturnsNotStartedWithoutGatewayOrDatabaseWrite() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStatusQueryService service = service(repository, gateway, heartbeatStore);

        OpencodeProcessStatusProbe probe = service.query(new OpencodeProcessId("ocp_missing"), TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.NOT_STARTED);
        assertThat(probe.process()).isEmpty();
        assertThat(probe.managerStatus()).isEqualTo("NOT_RUNNING");
        assertThat(probe.healthStatus()).isEqualTo("NOT_RUNNING");
        assertThat(probe.restartable()).isTrue();
        assertThat(gateway.healthCommands).isEmpty();
        assertThat(repository.savedProcesses).isEmpty();
        assertThat(heartbeatStore.recordedProcessIds).isEmpty();
    }

    @Test
    void healthyProcessRefreshesRunningSnapshotAndHeartbeat() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess old = process("ocp_running", 4097, OpencodeServerProcessStatus.UNHEALTHY, 11111L);
        repository.processes.put(old.processId(), old);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.healthy("ok");
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStatusQueryService service = service(repository, gateway, heartbeatStore);

        OpencodeProcessStatusProbe probe = service.query(old.processId(), TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.RUNNING);
        assertThat(probe.process()).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
            assertThat(process.pid()).isEqualTo(11111L);
            assertThat(process.healthMessage()).isEqualTo("ok");
            assertThat(process.lastHealthCheckAt()).isEqualTo(NOW);
            assertThat(process.updatedAt()).isEqualTo(NOW);
        });
        assertThat(probe.managerStatus()).isEqualTo("RUNNING");
        assertThat(probe.healthStatus()).isEqualTo("HEALTHY");
        assertThat(probe.restartable()).isFalse();
        assertThat(repository.savedProcesses).containsKey(old.processId());
        assertThat(heartbeatStore.recordedProcessIds).containsExactly(old.processId());
    }

    @Test
    void healthyProcessRefreshesBaseUrlFromAdvertisedHostForStableServerId() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess old = process(
                "ocp_running",
                "linux-prod-a",
                "http://old-host:4097",
                4097,
                OpencodeServerProcessStatus.UNHEALTHY,
                11111L);
        repository.processes.put(old.processId(), old);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.healthy("ok");
        OpencodeProcessStatusQueryService service = new OpencodeProcessStatusQueryService(
                repository,
                gateway,
                new RecordingHeartbeatStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpencodeServerAddressResolver("10.8.0.21"));

        OpencodeProcessStatusProbe probe = service.query(old.processId(), TRACE_ID);

        assertThat(probe.process()).get()
                .extracting(OpencodeServerProcess::baseUrl)
                .isEqualTo("http://10.8.0.21:4097");
    }

    @Test
    void notRunningHealthMessageMarksProcessStoppedAndClearsPid() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess old = process("ocp_stale", 4097, OpencodeServerProcessStatus.RUNNING, 22222L);
        repository.processes.put(old.processId(), old);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("port 4097 is not managed");
        OpencodeProcessStatusQueryService service = service(repository, gateway, new RecordingHeartbeatStore());

        OpencodeProcessStatusProbe probe = service.query(old.processId(), TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.NOT_STARTED);
        assertThat(probe.process()).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.STOPPED);
            assertThat(process.pid()).isNull();
            assertThat(process.healthMessage()).isEqualTo("port 4097 is not managed");
        });
        assertThat(probe.managerStatus()).isEqualTo("NOT_RUNNING");
        assertThat(probe.healthStatus()).isEqualTo("NOT_RUNNING");
        assertThat(probe.restartable()).isTrue();
    }

    @Test
    void unhealthyHealthMessageReturnsStaleWithoutDatabaseWrite() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess old = process("ocp_unhealthy", 4097, OpencodeServerProcessStatus.RUNNING, 22222L);
        repository.processes.put(old.processId(), old);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("opencode http health failed");
        OpencodeProcessStatusQueryService service = service(repository, gateway, new RecordingHeartbeatStore());

        OpencodeProcessStatusProbe probe = service.query(old.processId(), TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.STALE);
        assertThat(probe.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
        assertThat(probe.process()).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
            assertThat(process.pid()).isEqualTo(22222L);
            assertThat(process.healthMessage()).isEqualTo("old");
        });
        assertThat(probe.managerStatus()).isEqualTo("STALE");
        assertThat(probe.healthStatus()).isEqualTo("STALE");
        assertThat(probe.restartable()).isFalse();
        assertThat(repository.savedProcesses).isEmpty();
    }

    @Test
    void healthCommandExceptionReturnsStaleAndKeepsErrorCodeWithoutDatabaseWrite() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess old = process("ocp_failed", 4097, OpencodeServerProcessStatus.RUNNING, 22222L);
        repository.processes.put(old.processId(), old);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthFailure = new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "manager command timeout");
        OpencodeProcessStatusQueryService service = service(repository, gateway, new RecordingHeartbeatStore());

        OpencodeProcessStatusProbe probe = service.query(old.processId(), TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.STALE);
        assertThat(probe.errorCode()).isEqualTo(ErrorCode.OPENCODE_TIMEOUT);
        assertThat(probe.process()).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
            assertThat(process.pid()).isEqualTo(22222L);
            assertThat(process.healthMessage()).isEqualTo("old");
        });
        assertThat(probe.managerStatus()).isEqualTo("STALE");
        assertThat(probe.healthStatus()).isEqualTo("STALE");
        assertThat(probe.restartable()).isFalse();
        assertThat(repository.savedProcesses).isEmpty();
    }

    private static OpencodeProcessStatusQueryService service(
            FakeRepository repository,
            RecordingGateway gateway,
            RecordingHeartbeatStore heartbeatStore) {
        return new OpencodeProcessStatusQueryService(
                repository,
                gateway,
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OpencodeServerProcess process(
            String processId,
            int port,
            OpencodeServerProcessStatus status,
            Long pid) {
        return process(processId, "10.8.0.12", "http://10.8.0.12:" + port, port, status, pid);
    }

    private static OpencodeServerProcess process(
            String processId,
            String linuxServerId,
            String baseUrl,
            int port,
            OpencodeServerProcessStatus status,
            Long pid) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId),
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId(linuxServerId),
                new OpencodeContainerId("ctr_01"),
                port,
                pid,
                baseUrl,
                status,
                "/data/opencode/session/" + port,
                "/data/opencode/.config/opencode/",
                NOW.minusSeconds(3600),
                NOW.minusSeconds(60),
                "old",
                NOW.minusSeconds(3600),
                NOW.minusSeconds(60),
                TRACE_ID);
    }

    private static final class RecordingGateway implements OpencodeProcessManagerGateway {
        private final List<OpencodeProcessHealthCommand> healthCommands = new ArrayList<>();
        private OpencodeProcessHealthResult health = OpencodeProcessHealthResult.healthy("ok");
        private RuntimeException healthFailure;

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            healthCommands.add(command);
            if (healthFailure != null) {
                throw healthFailure;
            }
            return health;
        }

        @Override public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) { throw new UnsupportedOperationException("startProcess is not used"); }
        @Override public OpencodeProcessControlResult restartProcess(OpencodeProcessControlCommand command) { throw new UnsupportedOperationException("restartProcess is not used"); }
        @Override public OpencodeProcessControlResult stopProcess(OpencodeProcessControlCommand command) { throw new UnsupportedOperationException("stopProcess is not used"); }
    }

    private static final class RecordingHeartbeatStore implements OpencodeProcessHeartbeatStore {
        private final List<OpencodeProcessId> recordedProcessIds = new ArrayList<>();

        @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { recordedProcessIds.add(processId); }
        @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) { }
        @Override public void recordBackendSnapshot(com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot snapshot) { }
        @Override public void recordManagerSnapshot(com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot snapshot) { }
        @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
        @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return new LinkedHashSet<>(recordedProcessIds); }
        @Override public List<com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
        @Override public List<com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
        @Override public void cleanupExpiredHeartbeats() { }
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository {
        private final Map<OpencodeProcessId, OpencodeServerProcess> processes = new LinkedHashMap<>();
        private final Map<OpencodeProcessId, OpencodeServerProcess> savedProcesses = new LinkedHashMap<>();

        @Override public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) { processes.put(process.processId(), process); savedProcesses.put(process.processId(), process); return process; }
        @Override public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) { return Optional.ofNullable(processes.get(processId)); }
        @Override public LinuxServer saveLinuxServer(LinuxServer linuxServer) { return linuxServer; }
        @Override public Optional<LinuxServer> findLinuxServerById(LinuxServerId linuxServerId) { return Optional.empty(); }
        @Override public BackendJavaProcess saveBackendJavaProcess(BackendJavaProcess backendJavaProcess) { return backendJavaProcess; }
        @Override public Optional<BackendJavaProcess> findBackendJavaProcessById(BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit) { return List.of(); }
        @Override public OpencodeContainer saveContainer(OpencodeContainer container) { return container; }
        @Override public Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId) { return Optional.empty(); }
        @Override public List<OpencodeContainer> findHealthyContainers(int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackend(BackendProcessId backendProcessId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackendByLinuxServer(BackendProcessId backendProcessId, LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public OpencodeContainerManager saveContainerManager(OpencodeContainerManager manager) { return manager; }
        @Override public Optional<OpencodeContainerManager> findContainerManagerById(ContainerManagerId managerId) { return Optional.empty(); }
        @Override public OpencodeManagerBackendConnection saveManagerBackendConnection(OpencodeManagerBackendConnection connection) { return connection; }
        @Override public Optional<OpencodeManagerBackendConnection> findManagerBackendConnection(ContainerManagerId managerId, BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) { return List.of(); }
        @Override public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) { return binding; }
        @Override public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) { return Optional.empty(); }
        @Override public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return List.copyOf(processes.values()); }
        @Override public PageResponse<OpencodeServerProcess> findOpencodeServerProcesses(OpencodeServerProcessFilter filter, PageRequest pageRequest) { return new PageResponse<>(List.copyOf(processes.values()), 1, processes.size(), processes.size()); }
    }
}
