package com.icbc.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterRepository;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
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
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class UserOpencodeProcessAssignmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-24T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @org.junit.jupiter.api.Test
    void statusRequestsInitializationWhenUserHasNoBindingAndContainerIsAvailable() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_busy", container("ctr_busy", "10.8.0.12", 4096, 4100, 4, 3));
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.13", 4200, 4205, 4, 0));
        UserOpencodeProcessAssignmentService service = service(repository, new RecordingGateway());

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.NEEDS_INITIALIZATION);
        assertThat(response.initializable()).isTrue();
        assertThat(response.processId()).isNull();
        assertThat(response.serviceStatus()).isEqualTo(UserOpencodeServiceStatus.UNASSIGNED);
        assertThat(response.serviceAddress()).isNull();
        assertThat(response.message()).contains("初始化");
    }

    @org.junit.jupiter.api.Test
    void initializeStartsProcessOnLeastLoadedContainerAndProjectsExecutionNode() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_busy", container("ctr_busy", "10.8.0.12", 4096, 4100, 4, 3));
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.13", 4200, 4205, 4, 0));
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessStatusResponse response = service.initialize(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        assertThat(response.baseUrl()).isEqualTo("http://10.8.0.13:4200");
        assertThat(gateway.startCommands).hasSize(1);
        assertThat(gateway.startCommands.getFirst().containerId()).isEqualTo(new OpencodeContainerId("ctr_idle"));
        assertThat(gateway.startCommands.getFirst().sessionPath()).isEqualTo(SESSION_DIR + "4200");
        assertThat(gateway.startCommands.getFirst().configPath()).isEqualTo(CONFIG_DIR);
        assertThat(repository.findUserBinding(USER_ID, "opencode")).get()
                .extracting(UserOpencodeProcessBinding::linuxServerId)
                .isEqualTo(new LinuxServerId("10.8.0.13"));
        assertThat(repository.savedNodes).hasSize(1);
        assertThat(repository.savedNodes.getFirst().baseUrl()).isEqualTo("http://10.8.0.13:4200");
    }

    @org.junit.jupiter.api.Test
    void statusReusesHealthyBoundProcess() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.12", 4096, 4100, 4, 1));
        OpencodeServerProcess process = process("ocp_existing", USER_ID, "10.8.0.12", "ctr_idle", 4096, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(process.processId().value(), process);
        repository.bindings.put(USER_ID.value() + ":opencode", binding(USER_ID, process.processId(), "10.8.0.12", 4096));
        UserOpencodeProcessAssignmentService service = service(repository, new RecordingGateway());

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        assertThat(response.processId()).isEqualTo("ocp_existing");
        assertThat(response.baseUrl()).isEqualTo("http://10.8.0.12:4096");
        assertThat(response.serviceStatus()).isEqualTo(UserOpencodeServiceStatus.RUNNING);
        assertThat(response.serviceAddress()).isEqualTo("10.8.0.12:4096");
    }

    @org.junit.jupiter.api.Test
    void statusReportsNotRunningWhenBoundProcessHealthFails() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.12", 4096, 4100, 4, 1));
        OpencodeServerProcess process = process("ocp_existing", USER_ID, "10.8.0.12", "ctr_idle", 4096, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(process.processId().value(), process);
        repository.bindings.put(USER_ID.value() + ":opencode", binding(USER_ID, process.processId(), "10.8.0.12", 4096));
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("down");
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.NEEDS_INITIALIZATION);
        assertThat(response.serviceStatus()).isEqualTo(UserOpencodeServiceStatus.NOT_RUNNING);
        assertThat(response.serviceAddress()).isEqualTo("10.8.0.12:4096");
    }

    @org.junit.jupiter.api.Test
    void initializeChoosesFirstFreePortInsideContainerRange() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.12", 4096, 4098, 3, 0));
        OpencodeServerProcess occupied = process(
                "ocp_occupied",
                new UserId("usr_occupied_123456"),
                "10.8.0.12",
                "ctr_idle",
                4096,
                OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(occupied.processId().value(), occupied);
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessStatusResponse response = service.initialize(USER_ID, "opencode", TRACE_ID);

        assertThat(response.port()).isEqualTo(4097);
        assertThat(gateway.startCommands.getFirst().baseUrl()).isEqualTo("http://10.8.0.12:4097");
    }

    @org.junit.jupiter.api.Test
    void initializeRebuildsUnhealthyBindingOnSameLinuxServer() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_old", container("ctr_old", "10.8.0.12", 4096, 4098, 3, 1));
        repository.containers.put("ctr_new", container("ctr_new", "10.8.0.12", 4200, 4202, 3, 0));
        repository.containers.put("ctr_other_linux", container("ctr_other_linux", "10.8.0.13", 4300, 4302, 3, 0));
        OpencodeServerProcess oldProcess = process("ocp_existing", USER_ID, "10.8.0.12", "ctr_old", 4096, OpencodeServerProcessStatus.UNHEALTHY);
        repository.processes.put(oldProcess.processId().value(), oldProcess);
        repository.bindings.put(USER_ID.value() + ":opencode", binding(USER_ID, oldProcess.processId(), "10.8.0.12", 4096));
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("down");
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessStatusResponse response = service.initialize(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        assertThat(response.linuxServerId()).isEqualTo("10.8.0.12");
        assertThat(response.containerId()).isEqualTo("ctr_new");
        assertThat(response.port()).isEqualTo(4200);
        assertThat(response.processId()).isEqualTo("ocp_existing");
        assertThat(gateway.startCommands.getFirst().linuxServerId()).isEqualTo(new LinuxServerId("10.8.0.12"));
    }

    @org.junit.jupiter.api.Test
    void initializeFailsWhenNoContainerIsAvailable() {
        FakeRepository repository = new FakeRepository();
        UserOpencodeProcessAssignmentService service = service(repository, new RecordingGateway());

        assertThatThrownBy(() -> service.initialize(USER_ID, "opencode", TRACE_ID))
                .isInstanceOf(PlatformException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
    }

    @org.junit.jupiter.api.Test
    void nonOpencodeAgentIsRejected() {
        UserOpencodeProcessAssignmentService service = service(new FakeRepository(), new RecordingGateway());

        assertThatThrownBy(() -> service.status(USER_ID, "otheragent", TRACE_ID))
                .isInstanceOf(PlatformException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @org.junit.jupiter.api.Test
    void initializePropagatesGatewayUnavailableAsPlatformError() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.12", 4096, 4100, 4, 0));
        RecordingGateway gateway = new RecordingGateway();
        gateway.startFailure = new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "管理进程尚未接入");
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        assertThatThrownBy(() -> service.initialize(USER_ID, "opencode", TRACE_ID))
                .isInstanceOf(PlatformException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
    }

    @org.junit.jupiter.api.Test
    void localDirectStatusReturnsSyntheticReadyWithoutTouchingRepository() {
        FakeRepository repository = new NoopRepository();
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = serviceLocalDirect(repository, gateway, "http://127.0.0.1:4096");

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        assertThat(response.baseUrl()).isEqualTo("http://127.0.0.1:4096");
        assertThat(response.port()).isEqualTo(4096);
        assertThat(response.linuxServerId()).isEqualTo("127.0.0.1");
        assertThat(response.processId()).isEqualTo("ocp_local_direct");
        assertThat(response.serviceStatus()).isEqualTo(UserOpencodeServiceStatus.RUNNING);
        assertThat(response.serviceAddress()).isEqualTo("127.0.0.1:4096");
        assertThat(response.message()).contains("本地开发模式");
        // 短路模式下不允许触发 gateway 健康检测，也不应写库。
        assertThat(gateway.startCommands).isEmpty();
        assertThat(repository.findUserBindingCalls).isEqualTo(0);
        assertThat(repository.findContainerCalls).isEqualTo(0);
    }

    @org.junit.jupiter.api.Test
    void localDirectInitializeReturnsSyntheticReadyAndSkipsGatewayStart() {
        FakeRepository repository = new NoopRepository();
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = serviceLocalDirect(repository, gateway, "http://127.0.0.1:4096");

        UserOpencodeProcessStatusResponse response = service.initialize(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        assertThat(response.baseUrl()).isEqualTo("http://127.0.0.1:4096");
        assertThat(response.message()).contains("本地开发模式");
        // 关键：initialize 也不调用 gateway.startProcess，避免被 manager 状态卡住。
        assertThat(gateway.startCommands).isEmpty();
        assertThat(repository.findUserBindingCalls).isEqualTo(0);
    }

    @org.junit.jupiter.api.Test
    void localDirectRequireReadyProcessReturnsSyntheticAssignment() {
        FakeRepository repository = new NoopRepository();
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = serviceLocalDirect(repository, gateway, "http://127.0.0.1:4096");

        UserOpencodeProcessAssignment assignment = service.requireReadyProcess(USER_ID, "opencode", TRACE_ID);

        assertThat(assignment.node().baseUrl()).isEqualTo("http://127.0.0.1:4096");
        assertThat(assignment.node().executionNodeId().value()).isEqualTo("node_ocp_local_direct");
        // 不应触发 topology / binding 查询，Run 启动可以走到 4096 直连。
        assertThat(repository.findUserBindingCalls).isEqualTo(0);
    }

    @org.junit.jupiter.api.Test
    void localDirectBaseUrlWithoutPortFallsBackToDefaults() {
        FakeRepository repository = new NoopRepository();
        RecordingGateway gateway = new RecordingGateway();
        // 故意传一个不能解析出 host/port 的字符串，验证服务会回退到默认 127.0.0.1:4096 而不是抛错。
        UserOpencodeProcessAssignmentService service = serviceLocalDirect(repository, gateway, "not a url");

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        assertThat(response.baseUrl()).isEqualTo("http://127.0.0.1:4096");
        assertThat(response.linuxServerId()).isEqualTo("127.0.0.1");
    }

    private static UserOpencodeProcessAssignmentService service(FakeRepository repository, RecordingGateway gateway) {
        return new UserOpencodeProcessAssignmentService(
                repository,
                commonParameters(),
                repository,
                gateway,
                new BackendJavaProcessLifecycleService(
                        repository,
                        new ManagerControlSettings(
                                "secret-token",
                                "http://10.8.0.21:8080",
                                new LinuxServerId("10.8.0.21"),
                                Duration.ofSeconds(10),
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(5),
                                100)));
    }

    private static final String SESSION_DIR = "/tmp/testagent/.session/";
    private static final String CONFIG_DIR = "/tmp/testagent/.config/opencode/";

    private static CommonParameterRepository commonParameters() {
        Map<String, String> parameters = Map.of(
                "OPENCODE_SESSION_DIR", SESSION_DIR,
                "OPENCODE_PUBLIC_CONFIG_DIR", CONFIG_DIR);
        return (englishName, platform) -> Optional.ofNullable(parameters.get(englishName))
                .map(value -> new CommonParameter(
                        "param_" + englishName.toLowerCase(),
                        englishName,
                        englishName,
                        value,
                        platform,
                        NOW,
                        NOW));
    }

    private static UserOpencodeProcessAssignmentService serviceLocalDirect(
            FakeRepository repository, RecordingGateway gateway, String baseUrl) {
        return new UserOpencodeProcessAssignmentService(
                repository,
                commonParameters(),
                repository,
                gateway,
                new BackendJavaProcessLifecycleService(
                        repository,
                        new ManagerControlSettings(
                                "secret-token",
                                "http://10.8.0.21:8080",
                                new LinuxServerId("10.8.0.21"),
                                Duration.ofSeconds(10),
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(5),
                                100)),
                new OpencodeProcessHeartbeatStore() {
                    @Override public void recordBackendHeartbeat(com.icbc.testagent.domain.opencodeprocess.BackendProcessId backendProcessId, Instant heartbeatAt) { }
                    @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) { }
                    @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) { }
                    @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { }
                    @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
                    @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
                    @Override public Set<com.icbc.testagent.domain.opencodeprocess.BackendProcessId> liveBackendProcessIds() { return Set.of(); }
                    @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
                    @Override public void cleanupExpiredHeartbeats() { }
                },
                new LocalDirectSettings(true, baseUrl));
    }

    private static OpencodeContainer container(
            String containerId,
            String linuxServerId,
            int portStart,
            int portEnd,
            int maxProcesses,
            int currentProcesses) {
        return new OpencodeContainer(
                new OpencodeContainerId(containerId),
                new LinuxServerId(linuxServerId),
                containerId,
                portStart,
                portEnd,
                maxProcesses,
                currentProcesses,
                com.icbc.testagent.domain.opencodeprocess.OpencodeContainerStatus.READY,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static OpencodeServerProcess process(
            String processId,
            UserId userId,
            String linuxServerId,
            String containerId,
            int port,
            OpencodeServerProcessStatus status) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId),
                userId,
                new LinuxServerId(linuxServerId),
                new OpencodeContainerId(containerId),
                port,
                12345L,
                "http://" + linuxServerId + ":" + port,
                status,
                "/data/opencode/session/" + port,
                "/data/opencode/.config/opencode/",
                NOW,
                NOW,
                "ok",
                NOW,
                NOW,
                TRACE_ID);
    }

    private static UserOpencodeProcessBinding binding(
            UserId userId,
            OpencodeProcessId processId,
            String linuxServerId,
            int port) {
        return new UserOpencodeProcessBinding(
                userId,
                "opencode",
                processId,
                new LinuxServerId(linuxServerId),
                port,
                UserOpencodeProcessBindingStatus.ACTIVE,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static final class RecordingGateway implements OpencodeProcessManagerGateway {
        private final List<OpencodeProcessStartCommand> startCommands = new ArrayList<>();
        private OpencodeProcessHealthResult health = OpencodeProcessHealthResult.healthy("ok");
        private PlatformException startFailure;

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            return health;
        }

        @Override
        public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) {
            if (startFailure != null) {
                throw startFailure;
            }
            startCommands.add(command);
            return new OpencodeProcessStartResult(12345L, "started");
        }
    }

    static class FakeRepository implements OpencodeProcessManagementRepository, ExecutionNodeRepository {
        private final Map<String, OpencodeContainer> containers = new LinkedHashMap<>();
        private final Map<String, OpencodeServerProcess> processes = new LinkedHashMap<>();
        private final Map<String, UserOpencodeProcessBinding> bindings = new LinkedHashMap<>();
        private final List<ExecutionNode> savedNodes = new ArrayList<>();
        int findUserBindingCalls;
        int findContainerCalls;

        @Override
        public List<OpencodeContainer> findHealthyContainers(int limit) {
            return containers.values().stream()
                    .filter(OpencodeContainer::canAcceptProcess)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit) {
            return containers.values().stream()
                    .filter(container -> container.linuxServerId().equals(linuxServerId))
                    .filter(OpencodeContainer::canAcceptProcess)
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<OpencodeContainer> findHealthyContainersConnectedToBackend(BackendProcessId backendProcessId, int limit) {
            findContainerCalls++;
            return findHealthyContainers(limit);
        }

        @Override
        public List<OpencodeContainer> findHealthyContainersConnectedToBackendByLinuxServer(
                BackendProcessId backendProcessId,
                LinuxServerId linuxServerId,
                int limit) {
            findContainerCalls++;
            return findHealthyContainersByLinuxServer(linuxServerId, limit);
        }

        @Override
        public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) {
            return processes.values().stream()
                    .filter(process -> process.linuxServerId().equals(linuxServerId))
                    .filter(process -> process.containerId().equals(containerId))
                    .filter(process -> process.status() == OpencodeServerProcessStatus.STARTING
                            || process.status() == OpencodeServerProcessStatus.RUNNING)
                    .map(OpencodeServerProcess::port)
                    .toList();
        }

        @Override
        public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) {
            findUserBindingCalls++;
            return Optional.ofNullable(bindings.get(userId.value() + ":" + agentId.trim().toLowerCase()));
        }

        @Override
        public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) {
            processes.put(process.processId().value(), process);
            return process;
        }

        @Override
        public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) {
            return Optional.ofNullable(processes.get(processId.value()));
        }

        @Override
        public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) {
            bindings.put(binding.userId().value() + ":" + binding.agentId(), binding);
            return binding;
        }

        @Override
        public ExecutionNode save(ExecutionNode executionNode) {
            savedNodes.add(executionNode);
            return executionNode;
        }

        @Override
        public Optional<ExecutionNode> findById(ExecutionNodeId executionNodeId) {
            return savedNodes.stream()
                    .filter(node -> node.executionNodeId().equals(executionNodeId))
                    .findFirst();
        }

        @Override
        public List<ExecutionNode> findRoutableNodes(int limit) {
            return savedNodes.stream().limit(limit).toList();
        }

        @Override public LinuxServer saveLinuxServer(LinuxServer linuxServer) { return linuxServer; }
        @Override public Optional<LinuxServer> findLinuxServerById(LinuxServerId linuxServerId) { return Optional.empty(); }
        @Override public BackendJavaProcess saveBackendJavaProcess(BackendJavaProcess backendJavaProcess) { return backendJavaProcess; }
        @Override public Optional<BackendJavaProcess> findBackendJavaProcessById(BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit) { return List.of(); }
        @Override public OpencodeContainer saveContainer(OpencodeContainer container) { containers.put(container.containerId().value(), container); return container; }
        @Override public Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId) { return Optional.ofNullable(containers.get(containerId.value())); }
        @Override public OpencodeContainerManager saveContainerManager(OpencodeContainerManager manager) { return manager; }
        @Override public Optional<OpencodeContainerManager> findContainerManagerById(ContainerManagerId managerId) { return Optional.empty(); }
        @Override public OpencodeManagerBackendConnection saveManagerBackendConnection(OpencodeManagerBackendConnection connection) { return connection; }
        @Override public Optional<OpencodeManagerBackendConnection> findManagerBackendConnection(ContainerManagerId managerId, BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override
        public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return processes.values().stream().limit(limit).toList(); }
    }

    /**
     * 用于本地开发短路测试的占位 repository：抛错意味着如果服务真的去查库，
     * 测试会立即失败，便于保证短路路径不接触数据库。
     */
    private static final class NoopRepository extends FakeRepository {
        @Override
        public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) {
            throw new AssertionError("local-direct 不应写库: " + process);
        }
        @Override
        public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) {
            throw new AssertionError("local-direct 不应写库: " + binding);
        }
        @Override
        public ExecutionNode save(ExecutionNode executionNode) {
            throw new AssertionError("local-direct 不应写库: " + executionNode);
        }
    }
}
