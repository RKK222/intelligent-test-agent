package com.icbc.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
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
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.icbc.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeManagementQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-24T00:00:00Z");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void overviewAggregatesTopologyAndMergesUserBindings() {
        FakeRepository repository = new FakeRepository();
        LinuxServer linuxServer = linuxServer();
        BackendJavaProcess backendProcess = backendProcess();
        OpencodeContainer container = container();
        OpencodeContainerManager manager = manager();
        OpencodeManagerBackendConnection connection = connection();
        OpencodeServerProcess process = process("ocp_1234567890abcdef", "usr_1234567890abcdef", OpencodeServerProcessStatus.RUNNING);
        UserOpencodeProcessBinding binding = binding(process);
        repository.linuxServers.put(linuxServer.linuxServerId(), linuxServer);
        repository.backendProcesses.put(backendProcess.backendProcessId(), backendProcess);
        repository.containers.put(container.containerId(), container);
        repository.managers.put(manager.managerId(), manager);
        repository.connections.add(connection);
        repository.processes.add(process);
        repository.bindings.put(process.processId(), binding);
        RuntimeManagementQueryService service = new RuntimeManagementQueryService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RuntimeManagementOverview overview = service.overview(
                new OpencodeServerProcessFilter(
                        OpencodeServerProcessStatus.RUNNING,
                        new LinuxServerId("10.8.0.12"),
                        new OpencodeContainerId("ctr_01"),
                        new UserId("usr_1234567890abcdef")),
                new PageRequest(1, 20),
                TRACE_ID);

        assertThat(overview.generatedAt()).isEqualTo(NOW);
        assertThat(overview.summary().linuxServers()).isEqualTo(1);
        assertThat(overview.summary().readyBackendProcesses()).isEqualTo(1);
        assertThat(overview.summary().connectedManagers()).isEqualTo(1);
        assertThat(overview.summary().runningOpencodeProcesses()).isEqualTo(1);
        assertThat(overview.summary().userBindings()).isEqualTo(1);
        assertThat(overview.linuxServers()).containsExactly(linuxServer);
        assertThat(overview.backendProcesses()).containsExactly(backendProcess);
        assertThat(overview.containers()).containsExactly(container);
        assertThat(overview.managers()).containsExactly(manager);
        assertThat(overview.managerBackendConnections()).containsExactly(connection);
        assertThat(overview.opencodeProcesses().items()).hasSize(1);
        assertThat(overview.opencodeProcesses().items().getFirst().process()).isEqualTo(process);
        assertThat(overview.opencodeProcesses().items().getFirst().binding()).contains(binding);
        assertThat(repository.lastFilter.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
        assertThat(repository.lastPageRequest.size()).isEqualTo(20);
    }

    @Test
    void overviewHandlesEmptyTopology() {
        RuntimeManagementQueryService service = new RuntimeManagementQueryService(
                new FakeRepository(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        RuntimeManagementOverview overview = service.overview(OpencodeServerProcessFilter.empty(), new PageRequest(1, 20), TRACE_ID);

        assertThat(overview.summary().linuxServers()).isZero();
        assertThat(overview.summary().opencodeProcesses()).isZero();
        assertThat(overview.opencodeProcesses().items()).isEmpty();
        assertThat(overview.opencodeProcesses().total()).isZero();
    }

    @Test
    void overviewCountsRunningProcessesAcrossAllPages() {
        FakeRepository repository = new FakeRepository();
        repository.processes.add(process("ocp_1111111111111111", "usr_1111111111111111", OpencodeServerProcessStatus.RUNNING));
        repository.processes.add(process("ocp_2222222222222222", "usr_2222222222222222", OpencodeServerProcessStatus.RUNNING));
        repository.processes.add(process("ocp_3333333333333333", "usr_3333333333333333", OpencodeServerProcessStatus.STOPPED));
        RuntimeManagementQueryService service = new RuntimeManagementQueryService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RuntimeManagementOverview overview = service.overview(OpencodeServerProcessFilter.empty(), new PageRequest(1, 1), TRACE_ID);

        assertThat(overview.opencodeProcesses().items()).hasSize(1);
        assertThat(overview.opencodeProcesses().total()).isEqualTo(3);
        assertThat(overview.summary().runningOpencodeProcesses()).isEqualTo(2);
    }

    @Test
    void overviewReturnsZeroRunningCountWhenStatusFilterExcludesRunning() {
        FakeRepository repository = new FakeRepository();
        repository.processes.add(process("ocp_1111111111111111", "usr_1111111111111111", OpencodeServerProcessStatus.RUNNING));
        repository.processes.add(process("ocp_2222222222222222", "usr_2222222222222222", OpencodeServerProcessStatus.FAILED));
        RuntimeManagementQueryService service = new RuntimeManagementQueryService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RuntimeManagementOverview overview = service.overview(
                new OpencodeServerProcessFilter(OpencodeServerProcessStatus.FAILED, null, null, null),
                new PageRequest(1, 20),
                TRACE_ID);

        assertThat(overview.opencodeProcesses().items()).hasSize(1);
        assertThat(overview.summary().runningOpencodeProcesses()).isZero();
    }


    private static LinuxServer linuxServer() {
        return new LinuxServer(
                new LinuxServerId("10.8.0.12"),
                "10.8.0.12",
                LinuxServerStatus.READY,
                Map.of("cpu", 4),
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static BackendJavaProcess backendProcess() {
        return new BackendJavaProcess(
                new BackendProcessId("bjp_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                "http://10.8.0.12:8080",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static OpencodeContainer container() {
        return new OpencodeContainer(
                new OpencodeContainerId("ctr_01"),
                new LinuxServerId("10.8.0.12"),
                "opencode-a",
                4096,
                4100,
                4,
                1,
                OpencodeContainerStatus.READY,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static OpencodeContainerManager manager() {
        return new OpencodeContainerManager(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new LinuxServerId("10.8.0.12"),
                "opencode-manager.v1",
                ManagerConnectionStatus.CONNECTED,
                Map.of("start", true),
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static OpencodeManagerBackendConnection connection() {
        return new OpencodeManagerBackendConnection(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new BackendProcessId("bjp_1234567890abcdef"),
                ManagerConnectionStatus.CONNECTED,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static OpencodeServerProcess process(String processId, String userId, OpencodeServerProcessStatus status) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId),
                new UserId(userId),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4096,
                12345L,
                "http://10.8.0.12:4096",
                status,
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                NOW,
                NOW,
                "ok",
                NOW,
                NOW,
                TRACE_ID);
    }

    private static UserOpencodeProcessBinding binding(OpencodeServerProcess process) {
        return new UserOpencodeProcessBinding(
                process.userId(),
                "opencode",
                process.processId(),
                process.linuxServerId(),
                process.port(),
                UserOpencodeProcessBindingStatus.ACTIVE,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository {
        private final Map<LinuxServerId, LinuxServer> linuxServers = new LinkedHashMap<>();
        private final Map<BackendProcessId, BackendJavaProcess> backendProcesses = new LinkedHashMap<>();
        private final Map<OpencodeContainerId, OpencodeContainer> containers = new LinkedHashMap<>();
        private final Map<ContainerManagerId, OpencodeContainerManager> managers = new LinkedHashMap<>();
        private final List<OpencodeManagerBackendConnection> connections = new java.util.ArrayList<>();
        private final List<OpencodeServerProcess> processes = new java.util.ArrayList<>();
        private final Map<OpencodeProcessId, UserOpencodeProcessBinding> bindings = new LinkedHashMap<>();
        private OpencodeServerProcessFilter lastFilter;
        private PageRequest lastPageRequest;

        @Override public List<LinuxServer> findLinuxServers(int limit) { return linuxServers.values().stream().limit(limit).toList(); }
        @Override public List<BackendJavaProcess> findBackendJavaProcesses(int limit) { return backendProcesses.values().stream().limit(limit).toList(); }
        @Override public List<OpencodeContainer> findContainers(int limit) { return containers.values().stream().limit(limit).toList(); }
        @Override public List<OpencodeContainerManager> findContainerManagers(int limit) { return managers.values().stream().limit(limit).toList(); }
        @Override public List<OpencodeManagerBackendConnection> findManagerBackendConnections(int limit) { return connections.stream().limit(limit).toList(); }
        @Override public long countUserBindings() { return bindings.size(); }

        @Override
        public PageResponse<OpencodeServerProcess> findOpencodeServerProcesses(
                OpencodeServerProcessFilter filter,
                PageRequest pageRequest) {
            lastFilter = filter;
            lastPageRequest = pageRequest;
            List<OpencodeServerProcess> filtered = filterProcesses(filter);
            return new PageResponse<>(
                    filtered.stream()
                            .skip(pageRequest.offset())
                            .limit(pageRequest.size())
                            .toList(),
                    pageRequest.page(),
                    pageRequest.size(),
                    filtered.size());
        }

        @Override
        public long countOpencodeServerProcesses(OpencodeServerProcessFilter filter) {
            return filterProcesses(filter).size();
        }

        @Override
        public Map<OpencodeProcessId, UserOpencodeProcessBinding> findUserBindingsByProcessIds(List<OpencodeProcessId> processIds) {
            Map<OpencodeProcessId, UserOpencodeProcessBinding> found = new LinkedHashMap<>();
            for (OpencodeProcessId processId : processIds) {
                UserOpencodeProcessBinding binding = bindings.get(processId);
                if (binding != null) {
                    found.put(processId, binding);
                }
            }
            return found;
        }

        @Override public LinuxServer saveLinuxServer(LinuxServer linuxServer) { linuxServers.put(linuxServer.linuxServerId(), linuxServer); return linuxServer; }
        @Override public Optional<LinuxServer> findLinuxServerById(LinuxServerId linuxServerId) { return Optional.ofNullable(linuxServers.get(linuxServerId)); }
        @Override public BackendJavaProcess saveBackendJavaProcess(BackendJavaProcess backendJavaProcess) { backendProcesses.put(backendJavaProcess.backendProcessId(), backendJavaProcess); return backendJavaProcess; }
        @Override public Optional<BackendJavaProcess> findBackendJavaProcessById(BackendProcessId backendProcessId) { return Optional.ofNullable(backendProcesses.get(backendProcessId)); }
        @Override public List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit) { return List.of(); }
        @Override public OpencodeContainer saveContainer(OpencodeContainer container) { containers.put(container.containerId(), container); return container; }
        @Override public Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId) { return Optional.ofNullable(containers.get(containerId)); }
        @Override public List<OpencodeContainer> findHealthyContainers(int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackend(BackendProcessId backendProcessId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackendByLinuxServer(BackendProcessId backendProcessId, LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public OpencodeContainerManager saveContainerManager(OpencodeContainerManager manager) { managers.put(manager.managerId(), manager); return manager; }
        @Override public Optional<OpencodeContainerManager> findContainerManagerById(ContainerManagerId managerId) { return Optional.ofNullable(managers.get(managerId)); }
        @Override public OpencodeManagerBackendConnection saveManagerBackendConnection(OpencodeManagerBackendConnection connection) { connections.add(connection); return connection; }
        @Override public Optional<OpencodeManagerBackendConnection> findManagerBackendConnection(ContainerManagerId managerId, BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) { processes.add(process); return process; }
        @Override public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) { return processes.stream().filter(process -> process.processId().equals(processId)).findFirst(); }
        @Override public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) { return List.of(); }
        @Override public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) { bindings.put(binding.processId(), binding); return binding; }
        @Override public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) { return Optional.empty(); }
        @Override public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return processes.stream().limit(limit).toList(); }

        private List<OpencodeServerProcess> filterProcesses(OpencodeServerProcessFilter filter) {
            return processes.stream()
                    .filter(process -> filter.status() == null || process.status() == filter.status())
                    .filter(process -> filter.linuxServerId() == null || process.linuxServerId().equals(filter.linuxServerId()))
                    .filter(process -> filter.containerId() == null || process.containerId().equals(filter.containerId()))
                    .filter(process -> filter.userId() == null || process.userId().equals(filter.userId()))
                    .toList();
        }
    }
}
