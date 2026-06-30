package com.icbc.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
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
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
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

class RuntimeManagementCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void restartManagedProcessDelegatesToGatewayByContainerAndPort() {
        RecordingGateway gateway = new RecordingGateway();
        RuntimeManagementCommandService service = new RuntimeManagementCommandService(gateway);

        OpencodeProcessControlResult result = service.restartManagedProcess(
                new OpencodeContainerId("ctr_01"),
                4096,
                "trace_1234567890abcdef");

        assertThat(gateway.restartCommands).singleElement().satisfies(command -> {
            assertThat(command.containerId()).isEqualTo(new OpencodeContainerId("ctr_01"));
            assertThat(command.port()).isEqualTo(4096);
            assertThat(command.traceId()).isEqualTo("trace_1234567890abcdef");
        });
        assertThat(result.command()).isEqualTo("restart");
        assertThat(result.status()).isEqualTo("STARTED");
    }

    @Test
    void restartStoppedUserProcessStartsSamePortAndVerifiesHealth() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess stopped = process("ocp_stopped", 4097, OpencodeServerProcessStatus.STOPPED);
        repository.processes.put(stopped.processId(), stopped);
        Instant bindingCreatedAt = NOW.minusSeconds(1800);
        repository.bindingsByProcessId.put(stopped.processId(), binding(stopped, bindingCreatedAt));
        RecordingGateway gateway = new RecordingGateway();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        RuntimeManagementCommandService service = service(repository, gateway, heartbeatStore);

        OpencodeProcessControlResult result = service.restartManagedProcess(new OpencodeContainerId("ctr_01"), 4097, TRACE_ID);

        assertThat(gateway.restartCommands).isEmpty();
        assertThat(gateway.startCommands).singleElement()
                .extracting(OpencodeProcessStartCommand::port)
                .isEqualTo(4097);
        assertThat(gateway.healthCommands).hasSize(1);
        assertThat(result.command()).isEqualTo("restart");
        assertThat(result.status()).isEqualTo("STARTED");
        assertThat(repository.findOpencodeServerProcessById(stopped.processId())).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
            assertThat(process.pid()).isEqualTo(33333L);
        });
        assertThat(repository.bindingsByProcessId.get(stopped.processId()).createdAt()).isEqualTo(bindingCreatedAt);
        assertThat(heartbeatStore.liveOpencodeProcessIds()).contains(stopped.processId());
    }

    @Test
    void restartFallsBackToStartWhenManagerStateIsMissing() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess unhealthy = process("ocp_unhealthy", 4097, OpencodeServerProcessStatus.UNHEALTHY);
        repository.processes.put(unhealthy.processId(), unhealthy);
        RecordingGateway gateway = new RecordingGateway();
        gateway.restartFailure = new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "port 4097 is not managed");
        RuntimeManagementCommandService service = service(repository, gateway, new RecordingHeartbeatStore());

        OpencodeProcessControlResult result = service.restartManagedProcess(new OpencodeContainerId("ctr_01"), 4097, TRACE_ID);

        assertThat(gateway.restartCommands).hasSize(1);
        assertThat(gateway.startCommands).hasSize(1);
        assertThat(gateway.healthCommands).hasSize(1);
        assertThat(result.status()).isEqualTo("STARTED");
    }

    @Test
    void restartDoesNotReturnSuccessWhenPostStartHealthFails() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess stopped = process("ocp_stopped", 4097, OpencodeServerProcessStatus.STOPPED);
        repository.processes.put(stopped.processId(), stopped);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("opencode http health failed");
        RuntimeManagementCommandService service = service(repository, gateway, new RecordingHeartbeatStore());

        assertThatThrownBy(() -> service.restartManagedProcess(new OpencodeContainerId("ctr_01"), 4097, TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        assertThat(repository.findOpencodeServerProcessById(stopped.processId())).get()
                .extracting(OpencodeServerProcess::status)
                .isEqualTo(OpencodeServerProcessStatus.UNHEALTHY);
    }

    @Test
    void stopManagedProcessDelegatesToGatewayByContainerAndPort() {
        RecordingGateway gateway = new RecordingGateway();
        RuntimeManagementCommandService service = new RuntimeManagementCommandService(gateway);

        OpencodeProcessControlResult result = service.stopManagedProcess(
                new OpencodeContainerId("ctr_01"),
                4097,
                "trace_1234567890abcdef");

        assertThat(gateway.stopCommands).singleElement().satisfies(command -> {
            assertThat(command.containerId()).isEqualTo(new OpencodeContainerId("ctr_01"));
            assertThat(command.port()).isEqualTo(4097);
            assertThat(command.traceId()).isEqualTo("trace_1234567890abcdef");
        });
        assertThat(result.command()).isEqualTo("stop");
        assertThat(result.status()).isEqualTo("STOPPED");
    }

    private static RuntimeManagementCommandService service(
            FakeRepository repository,
            RecordingGateway gateway,
            RecordingHeartbeatStore heartbeatStore) {
        OpencodeProcessStartupService startupService = new OpencodeProcessStartupService(
                repository,
                repository,
                gateway,
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new RuntimeManagementCommandService(gateway, repository, startupService);
    }

    private static OpencodeServerProcess process(String processId, int port, OpencodeServerProcessStatus status) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId),
                USER_ID,
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                port,
                11111L,
                "http://10.8.0.12:" + port,
                status,
                "/data/opencode/session/" + port,
                "/data/opencode/.config/opencode/",
                NOW,
                NOW,
                "old",
                NOW.minusSeconds(3600),
                NOW,
                TRACE_ID);
    }

    private static UserOpencodeProcessBinding binding(OpencodeServerProcess process, Instant createdAt) {
        return new UserOpencodeProcessBinding(
                process.userId(),
                "opencode",
                process.processId(),
                process.linuxServerId(),
                process.port(),
                UserOpencodeProcessBindingStatus.ACTIVE,
                createdAt,
                NOW,
                TRACE_ID);
    }

    private static final class RecordingGateway implements OpencodeProcessManagerGateway {
        private final List<OpencodeProcessControlCommand> restartCommands = new ArrayList<>();
        private final List<OpencodeProcessControlCommand> stopCommands = new ArrayList<>();
        private final List<OpencodeProcessStartCommand> startCommands = new ArrayList<>();
        private final List<OpencodeProcessHealthCommand> healthCommands = new ArrayList<>();
        private PlatformException restartFailure;
        private OpencodeProcessHealthResult health = OpencodeProcessHealthResult.healthy("ok");

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            healthCommands.add(command);
            return health;
        }

        @Override
        public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) {
            startCommands.add(command);
            return new OpencodeProcessStartResult(33333L, "started");
        }

        @Override
        public OpencodeProcessControlResult restartProcess(OpencodeProcessControlCommand command) {
            restartCommands.add(command);
            if (restartFailure != null) {
                throw restartFailure;
            }
            return new OpencodeProcessControlResult(
                    "restart",
                    "STARTED",
                    command.port(),
                    12345L,
                    "http://10.8.0.12:4096",
                    "/data/opencode/session/4096",
                    "/data/opencode/.config/opencode/",
                    true,
                    "opencode server started",
                    command.traceId());
        }

        @Override
        public OpencodeProcessControlResult stopProcess(OpencodeProcessControlCommand command) {
            stopCommands.add(command);
            return new OpencodeProcessControlResult(
                    "stop",
                    "STOPPED",
                    command.port(),
                    22345L,
                    "http://10.8.0.12:4097",
                    "/data/opencode/session/4097",
                    "/data/opencode/.config/opencode/",
                    true,
                    "opencode server stopped",
                    command.traceId());
        }
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository, ExecutionNodeRepository {
        private final Map<OpencodeProcessId, OpencodeServerProcess> processes = new LinkedHashMap<>();
        private final Map<OpencodeProcessId, UserOpencodeProcessBinding> bindingsByProcessId = new LinkedHashMap<>();
        private final List<ExecutionNode> nodes = new ArrayList<>();

        @Override
        public PageResponse<OpencodeServerProcess> findOpencodeServerProcesses(
                OpencodeServerProcessFilter filter,
                PageRequest pageRequest) {
            List<OpencodeServerProcess> matched = processes.values().stream()
                    .filter(process -> filter == null || filter.containerId() == null
                            || process.containerId().equals(filter.containerId()))
                    .toList();
            return new PageResponse<>(
                    matched.stream().skip(pageRequest.offset()).limit(pageRequest.size()).toList(),
                    pageRequest.page(),
                    pageRequest.size(),
                    matched.size());
        }

        @Override public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) { processes.put(process.processId(), process); return process; }
        @Override public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) { return Optional.ofNullable(processes.get(processId)); }
        @Override public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) { bindingsByProcessId.put(binding.processId(), binding); return binding; }
        @Override public Map<OpencodeProcessId, UserOpencodeProcessBinding> findUserBindingsByProcessIds(List<OpencodeProcessId> processIds) {
            return bindingsByProcessId.entrySet().stream()
                    .filter(entry -> processIds.contains(entry.getKey()))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        @Override public ExecutionNode save(ExecutionNode executionNode) { nodes.add(executionNode); return executionNode; }
        @Override public Optional<ExecutionNode> findById(ExecutionNodeId executionNodeId) { return Optional.empty(); }
        @Override public List<ExecutionNode> findRoutableNodes(int limit) { return nodes.stream().limit(limit).toList(); }
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
        @Override public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) { return Optional.empty(); }
        @Override public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return List.copyOf(processes.values()); }
    }

    private static final class RecordingHeartbeatStore implements OpencodeProcessHeartbeatStore {
        private final Set<OpencodeProcessId> liveOpencodeProcessIds = new LinkedHashSet<>();

        @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) { }
        @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) { }
        @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) { }
        @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { liveOpencodeProcessIds.add(processId); }
        @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
        @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
        @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
        @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.copyOf(liveOpencodeProcessIds); }
        @Override public void cleanupExpiredHeartbeats() { }
    }
}
