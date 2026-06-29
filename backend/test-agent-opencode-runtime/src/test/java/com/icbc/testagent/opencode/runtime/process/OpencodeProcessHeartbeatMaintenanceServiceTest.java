package com.icbc.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
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

class OpencodeProcessHeartbeatMaintenanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-24T00:00:00Z");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void refreshRunningProcessHeartbeatsRecordsOnlyHealthyProcessAndMarksDeadProcessUnhealthy() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess healthy = process("ocp_1111111111111111", 4096, OpencodeServerProcessStatus.RUNNING);
        OpencodeServerProcess unhealthy = process("ocp_2222222222222222", 4097, OpencodeServerProcessStatus.RUNNING);
        OpencodeServerProcess unavailable = process("ocp_3333333333333333", 4098, OpencodeServerProcessStatus.RUNNING);
        OpencodeServerProcess stopped = process("ocp_4444444444444444", 4099, OpencodeServerProcessStatus.STOPPED);
        repository.processes.addAll(List.of(healthy, unhealthy, unavailable, stopped));
        FakeGateway gateway = new FakeGateway();
        gateway.healthResults.put(healthy.processId(), OpencodeProcessHealthResult.healthy("ok"));
        gateway.healthResults.put(unhealthy.processId(), OpencodeProcessHealthResult.unhealthy("down"));
        gateway.unavailableProcessIds.add(unavailable.processId());
        FakeHeartbeatStore heartbeatStore = new FakeHeartbeatStore();
        OpencodeProcessHeartbeatMaintenanceService service = new OpencodeProcessHeartbeatMaintenanceService(
                repository,
                gateway,
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.refreshRunningProcessHeartbeats(TRACE_ID);

        assertThat(heartbeatStore.opencodeHeartbeats).containsExactly(healthy.processId());
        assertThat(repository.savedProcesses).containsOnlyKeys(healthy.processId(), unhealthy.processId(), unavailable.processId());
        assertThat(repository.savedProcesses.get(healthy.processId()).status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
        assertThat(repository.savedProcesses.get(unhealthy.processId()).status()).isEqualTo(OpencodeServerProcessStatus.UNHEALTHY);
        assertThat(repository.savedProcesses.get(unavailable.processId()).status()).isEqualTo(OpencodeServerProcessStatus.UNHEALTHY);
        assertThat(repository.savedProcesses.get(stopped.processId())).isNull();
        assertThat(repository.savedProcesses.values()).allSatisfy(process -> {
            assertThat(process.lastHealthCheckAt()).isEqualTo(NOW);
            assertThat(process.updatedAt()).isEqualTo(NOW);
            assertThat(process.traceId()).isEqualTo(TRACE_ID);
        });
        assertThat(repository.lastPageRequest.size()).isEqualTo(PageRequest.MAX_SIZE);
    }

    @Test
    void cleanupExpiredHeartbeatsDelegatesToStore() {
        FakeHeartbeatStore heartbeatStore = new FakeHeartbeatStore();
        OpencodeProcessHeartbeatMaintenanceService service = new OpencodeProcessHeartbeatMaintenanceService(
                new FakeRepository(),
                new FakeGateway(),
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.cleanupExpiredHeartbeats();

        assertThat(heartbeatStore.cleanupCalled).isTrue();
    }

    private static OpencodeServerProcess process(String processId, int port, OpencodeServerProcessStatus status) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId),
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                port,
                12345L,
                "http://10.8.0.12:" + port,
                status,
                "/data/opencode/session/" + port,
                "/data/opencode/.config/opencode/",
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                "old",
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                TRACE_ID);
    }

    private static final class FakeGateway implements OpencodeProcessManagerGateway {
        private final Map<OpencodeProcessId, OpencodeProcessHealthResult> healthResults = new LinkedHashMap<>();
        private final Set<OpencodeProcessId> unavailableProcessIds = new LinkedHashSet<>();

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            if (unavailableProcessIds.contains(command.processId())) {
                throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "管理进程不可用");
            }
            return healthResults.getOrDefault(command.processId(), OpencodeProcessHealthResult.unhealthy("missing"));
        }

        @Override
        public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) {
            throw new UnsupportedOperationException("startProcess is not used");
        }

        @Override
        public OpencodeProcessControlResult restartProcess(OpencodeProcessControlCommand command) {
            throw new UnsupportedOperationException("restartProcess is not used");
        }

        @Override
        public OpencodeProcessControlResult stopProcess(OpencodeProcessControlCommand command) {
            throw new UnsupportedOperationException("stopProcess is not used");
        }
    }

    private static final class FakeHeartbeatStore implements OpencodeProcessHeartbeatStore {
        private final List<OpencodeProcessId> opencodeHeartbeats = new ArrayList<>();
        private boolean cleanupCalled;

        @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) {}
        @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) {}
        @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) {}
        @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { opencodeHeartbeats.add(processId); }
        @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
        @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
        @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
        @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.copyOf(opencodeHeartbeats); }
        @Override public void cleanupExpiredHeartbeats() { cleanupCalled = true; }
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository {
        private final List<OpencodeServerProcess> processes = new ArrayList<>();
        private final Map<OpencodeProcessId, OpencodeServerProcess> savedProcesses = new LinkedHashMap<>();
        private PageRequest lastPageRequest;

        @Override
        public PageResponse<OpencodeServerProcess> findOpencodeServerProcesses(
                OpencodeServerProcessFilter filter,
                PageRequest pageRequest) {
            lastPageRequest = pageRequest;
            List<OpencodeServerProcess> filtered = processes.stream()
                    .filter(process -> filter.status() == null || process.status() == filter.status())
                    .filter(process -> filter.linuxServerId() == null || process.linuxServerId().equals(filter.linuxServerId()))
                    .filter(process -> filter.containerId() == null || process.containerId().equals(filter.containerId()))
                    .filter(process -> filter.userId() == null || process.userId().equals(filter.userId()))
                    .toList();
            return new PageResponse<>(
                    filtered.stream().skip(pageRequest.offset()).limit(pageRequest.size()).toList(),
                    pageRequest.page(),
                    pageRequest.size(),
                    filtered.size());
        }

        @Override
        public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) {
            savedProcesses.put(process.processId(), process);
            processes.removeIf(current -> current.processId().equals(process.processId()));
            processes.add(process);
            return process;
        }

        @Override public LinuxServer saveLinuxServer(LinuxServer linuxServer) { throw new UnsupportedOperationException(); }
        @Override public Optional<LinuxServer> findLinuxServerById(LinuxServerId linuxServerId) { return Optional.empty(); }
        @Override public BackendJavaProcess saveBackendJavaProcess(BackendJavaProcess backendJavaProcess) { throw new UnsupportedOperationException(); }
        @Override public Optional<BackendJavaProcess> findBackendJavaProcessById(BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit) { return List.of(); }
        @Override public OpencodeContainer saveContainer(OpencodeContainer container) { throw new UnsupportedOperationException(); }
        @Override public Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId) { return Optional.empty(); }
        @Override public List<OpencodeContainer> findHealthyContainers(int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackend(BackendProcessId backendProcessId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackendByLinuxServer(BackendProcessId backendProcessId, LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public OpencodeContainerManager saveContainerManager(OpencodeContainerManager manager) { throw new UnsupportedOperationException(); }
        @Override public Optional<OpencodeContainerManager> findContainerManagerById(ContainerManagerId managerId) { return Optional.empty(); }
        @Override public OpencodeManagerBackendConnection saveManagerBackendConnection(OpencodeManagerBackendConnection connection) { throw new UnsupportedOperationException(); }
        @Override public Optional<OpencodeManagerBackendConnection> findManagerBackendConnection(ContainerManagerId managerId, BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) { return processes.stream().filter(process -> process.processId().equals(processId)).findFirst(); }
        @Override public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) { return List.of(); }
        @Override public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) { throw new UnsupportedOperationException(); }
        @Override public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) { return Optional.empty(); }
        @Override public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return processes.stream().limit(limit).toList(); }
    }
}
