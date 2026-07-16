package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperation;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStep;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerCommandNotDispatchedException;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerConnectionRegistry;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.junit.jupiter.api.io.TempDir;

class UserOpencodeProcessAssignmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-24T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @TempDir
    Path tempDir;

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
    void statusDoesNotAllocateFromDatabaseWhenRedisHasNoManagerHeartbeat() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_db_only", container("ctr_db_only", "10.8.0.12", 4096, 4100, 4, 0));
        UserOpencodeProcessAssignmentService service = serviceWithLiveContainers(
                repository,
                new RecordingGateway(),
                List.of());

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.UNAVAILABLE);
        assertThat(response.initializable()).isFalse();
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
        assertThat(response.baseUrl()).isEqualTo("http://10.8.0.21:4200");
        assertThat(gateway.startCommands).hasSize(1);
        assertThat(gateway.healthCommands).hasSize(1);
        assertThat(gateway.startCommands.getFirst().containerId()).isEqualTo(new OpencodeContainerId("ctr_idle"));
        assertThat(gateway.startCommands.getFirst().sessionPath()).isEqualTo(USER_SESSION_DIR);
        assertThat(gateway.startCommands.getFirst().configPath()).isEqualTo(CONFIG_DIR);
        assertThat(repository.findUserBinding(USER_ID, "opencode")).get()
                .extracting(UserOpencodeProcessBinding::linuxServerId)
                .isEqualTo(new LinuxServerId("10.8.0.13"));
        assertThat(repository.savedNodes).hasSize(1);
        assertThat(repository.savedNodes.getFirst().baseUrl()).isEqualTo("http://10.8.0.21:4200");
    }

    @org.junit.jupiter.api.Test
    void initializeUsesRedisRealtimeLoadInsteadOfDatabaseCurrentProcesses() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_a", container("ctr_a", "10.8.0.12", 4096, 4100, 4, 0));
        repository.containers.put("ctr_b", container("ctr_b", "10.8.0.13", 4200, 4205, 4, 3));
        List<OpencodeContainer> liveContainers = List.of(
                container("ctr_a", "10.8.0.12", 4096, 4100, 4, 3),
                container("ctr_b", "10.8.0.13", 4200, 4205, 4, 0));
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = serviceWithLiveContainers(repository, gateway, liveContainers);

        service.initialize(USER_ID, "opencode", TRACE_ID);

        assertThat(gateway.startCommands).singleElement()
                .extracting(OpencodeProcessStartCommand::containerId)
                .isEqualTo(new OpencodeContainerId("ctr_b"));
    }

    @org.junit.jupiter.api.Test
    void initializeRejectsUnsafeUnifiedAuthIdForSessionDirectory() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.13", 4200, 4205, 4, 0));
        UserId badUserId = new UserId("usr_bad_1234567890abcdef");
        repository.users.put(badUserId.value(), User.createNew(
                badUserId.value(),
                "../bad/ucid",
                "bad-user",
                "password-hash",
                "org",
                "rd",
                "dept"));
        UserOpencodeProcessAssignmentService service = service(repository, new RecordingGateway());

        assertThatThrownBy(() -> service.initialize(badUserId, "opencode", TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @org.junit.jupiter.api.Test
    void initializeRecordsProgressStepsWhenOperationIdProvided() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.13", 4200, 4205, 4, 0));
        RecordingStartOperationRepository operations = new RecordingStartOperationRepository();
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = service(repository, gateway, operations);

        UserOpencodeProcessStatusResponse response = service.initialize(USER_ID, "opencode", TRACE_ID, "opi_1234567890abcdef");

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        OpencodeProcessStartOperation operation = operations.findById("opi_1234567890abcdef", USER_ID).orElseThrow();
        assertThat(operation.status()).isEqualTo(OpencodeProcessStartOperationStatus.SUCCEEDED);
        assertThat(operation.currentStep()).isEqualTo(OpencodeProcessStartOperationStep.COMPLETED);
        assertThat(operation.processId()).isEqualTo(response.processId());
        assertThat(operation.serviceAddress()).isEqualTo("10.8.0.21:4200");
        assertThat(operations.steps).containsSubsequence(
                OpencodeProcessStartOperationStep.VALIDATING_REQUEST,
                OpencodeProcessStartOperationStep.CHECKING_ASSIGNMENT,
                OpencodeProcessStartOperationStep.SELECTING_CONTAINER,
                OpencodeProcessStartOperationStep.PREPARING_STARTUP,
                OpencodeProcessStartOperationStep.STARTING_PROCESS,
                OpencodeProcessStartOperationStep.SAVING_CANDIDATE,
                OpencodeProcessStartOperationStep.CHECKING_PROCESS,
                OpencodeProcessStartOperationStep.HEALTH_CHECKING,
                OpencodeProcessStartOperationStep.SAVING_BINDING,
                OpencodeProcessStartOperationStep.COMPLETED);
    }

    @org.junit.jupiter.api.Test
    void initializeUsesAdvertisedHostForBaseUrlWhenLinuxServerIdIsStableIdentity() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "linux-prod-a", 4200, 4205, 4, 0));
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = service(repository, gateway, "linux-prod-a", "10.8.0.21");

        UserOpencodeProcessStatusResponse response = service.initialize(USER_ID, "opencode", TRACE_ID);

        assertThat(gateway.startCommands).singleElement().satisfies(command -> {
            assertThat(command.linuxServerId()).isEqualTo(new LinuxServerId("linux-prod-a"));
            assertThat(command.baseUrl()).isEqualTo("http://10.8.0.21:4200");
        });
        assertThat(response.serviceAddress()).isEqualTo("10.8.0.21:4200");
        assertThat(repository.savedNodes.getFirst().baseUrl()).isEqualTo("http://10.8.0.21:4200");
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
    void statusRechecksStoppedBindingAndRestoresRunningWhenManagerIsHealthy() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.12", 4096, 4100, 4, 1));
        OpencodeServerProcess process = process("ocp_existing", USER_ID, "10.8.0.12", "ctr_idle", 4096, OpencodeServerProcessStatus.STOPPED);
        repository.processes.put(process.processId().value(), process);
        repository.bindings.put(USER_ID.value() + ":opencode", binding(USER_ID, process.processId(), "10.8.0.12", 4096));
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        assertThat(gateway.healthCommands).hasSize(1);
        assertThat(repository.findOpencodeServerProcessById(process.processId())).get()
                .extracting(OpencodeServerProcess::status)
                .isEqualTo(OpencodeServerProcessStatus.RUNNING);
    }

    @org.junit.jupiter.api.Test
    void requireReadyProcessRechecksFailedBindingAndRestoresRunningWhenManagerIsHealthy() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess process = process("ocp_existing", USER_ID, "10.8.0.12", "ctr_idle", 4096, OpencodeServerProcessStatus.FAILED);
        repository.processes.put(process.processId().value(), process);
        repository.bindings.put(USER_ID.value() + ":opencode", binding(USER_ID, process.processId(), "10.8.0.12", 4096));
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessAssignment assignment = service.requireReadyProcess(USER_ID, "opencode", TRACE_ID);

        assertThat(assignment.node().baseUrl()).isEqualTo("http://10.8.0.12:4096");
        assertThat(repository.savedNodes).hasSize(1);
        assertThat(repository.findOpencodeServerProcessById(process.processId())).get()
                .extracting(OpencodeServerProcess::status)
                .isEqualTo(OpencodeServerProcessStatus.RUNNING);
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
    void statusKeepsReadyDuringShortStaleGracePeriod() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess process = withLastHealthCheckAt(
                process("ocp_recent", USER_ID, "10.8.0.12", "ctr_idle", 4096, OpencodeServerProcessStatus.RUNNING),
                Instant.now().minusSeconds(5));
        repository.processes.put(process.processId().value(), process);
        repository.bindings.put(USER_ID.value() + ":opencode", binding(USER_ID, process.processId(), "10.8.0.12", 4096));
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("temporary health failure");
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        assertThat(response.message()).contains("暂时无法确认");
    }

    @org.junit.jupiter.api.Test
    void requireReadyProcessRejectsStaleProcessAfterGracePeriod() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess process = process(
                "ocp_expired",
                USER_ID,
                "10.8.0.12",
                "ctr_idle",
                4096,
                OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(process.processId().value(), process);
        repository.bindings.put(USER_ID.value() + ":opencode", binding(USER_ID, process.processId(), "10.8.0.12", 4096));
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("persistent health failure");
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        assertThatThrownBy(() -> service.requireReadyProcess(USER_ID, "opencode", TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));
        assertThat(repository.savedNodes).isEmpty();
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
        assertThat(gateway.startCommands.getFirst().baseUrl()).isEqualTo("http://10.8.0.21:4097");
    }

    @org.junit.jupiter.api.Test
    void initializeSkipsCandidateWithoutDatabaseFreePort() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_a", container("ctr_a", "10.8.0.12", 4096, 4097, 2, 0));
        repository.containers.put("ctr_b", container("ctr_b", "10.8.0.13", 4200, 4201, 2, 1));
        repository.processes.put("ocp_used_4096", process(
                "ocp_used_4096", new UserId("usr_used_4096"), "10.8.0.12", "ctr_old", 4096,
                OpencodeServerProcessStatus.STOPPED));
        repository.processes.put("ocp_used_4097", process(
                "ocp_used_4097", new UserId("usr_used_4097"), "10.8.0.12", "ctr_old", 4097,
                OpencodeServerProcessStatus.UNHEALTHY));
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessStatusResponse response = service.initialize(USER_ID, "opencode", TRACE_ID);

        assertThat(response.containerId()).isEqualTo("ctr_b");
        assertThat(response.port()).isEqualTo(4200);
    }

    @org.junit.jupiter.api.Test
    void initializeSkipsDirtyPortsOnSameLinuxServer() {
        // 端口唯一约束按 linux_server_id 生效；同服务器其它容器和非运行态历史脏行也要避让。
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.12", 4096, 4098, 3, 0));
        repository.processes.put("ocp_dirty_4096", process(
                "ocp_dirty_4096",
                new UserId("usr_dirty_4096"),
                "10.8.0.12",
                "ctr_idle",
                4096,
                OpencodeServerProcessStatus.UNHEALTHY));
        repository.processes.put("ocp_other_container_4097", process(
                "ocp_other_container_4097",
                new UserId("usr_dirty_4097"),
                "10.8.0.12",
                "ctr_other",
                4097,
                OpencodeServerProcessStatus.STOPPED));
        UserOpencodeProcessAssignmentService service = service(repository, new RecordingGateway());

        UserOpencodeProcessStatusResponse response = service.initialize(USER_ID, "opencode", TRACE_ID);

        assertThat(response.port()).isEqualTo(4098);
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
        gateway.healthResults.add(OpencodeProcessHealthResult.unhealthy("down"));
        gateway.healthResults.add(OpencodeProcessHealthResult.healthy("ok"));
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
    void statusDoesNotUseGlobalContainerWhenBoundLinuxServerHasNoContainer() {
        // 多后端路由启用后，remote binding 必须由 binding 所属服务器的 Java 处理；
        // 业务服务自身不再把用户静默迁移到当前 Java 可见的其他服务器容器。
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_other_linux", container("ctr_other_linux", "10.8.0.13", 4300, 4302, 3, 0));
        OpencodeServerProcess oldProcess = process("ocp_existing", USER_ID, "10.8.0.12", "ctr_old", 4096, OpencodeServerProcessStatus.UNHEALTHY);
        repository.processes.put(oldProcess.processId().value(), oldProcess);
        repository.bindings.put(USER_ID.value() + ":opencode", binding(USER_ID, oldProcess.processId(), "10.8.0.12", 4096));
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("down");
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.UNAVAILABLE);
        assertThat(response.serviceStatus()).isEqualTo(UserOpencodeServiceStatus.NOT_RUNNING);
        assertThat(response.linuxServerId()).isEqualTo("10.8.0.12");
        assertThat(response.port()).isEqualTo(4096);
        assertThat(response.serviceAddress()).isEqualTo("10.8.0.12:4096");
    }

    @org.junit.jupiter.api.Test
    void statusDoesNotUseGlobalContainerWhenBoundProcessIsMissing() {
        // 旧 binding 的 process 行缺失时也不能 fallback 到其他服务器；
        // API 层会先把请求路由到 binding 所属服务器，路由失败则由入口层返回不可用。
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_other_linux", container("ctr_other_linux", "10.8.0.13", 4300, 4302, 3, 0));
        repository.bindings.put(
                USER_ID.value() + ":opencode",
                binding(USER_ID, new OpencodeProcessId("ocp_missing_process"), "10.8.0.12", 4096));
        UserOpencodeProcessAssignmentService service = service(repository, new RecordingGateway());

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.UNAVAILABLE);
        assertThat(response.serviceStatus()).isEqualTo(UserOpencodeServiceStatus.NOT_RUNNING);
        assertThat(response.linuxServerId()).isEqualTo("10.8.0.12");
        assertThat(response.port()).isEqualTo(4096);
        assertThat(response.serviceAddress()).isNull();
    }

    @org.junit.jupiter.api.Test
    void statusResolvesBindingAddressFromCurrentBackendWhenServerIdIsStableName() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_bound", container("ctr_bound", "server-a", 4096, 4100, 4, 0));
        repository.bindings.put(
                USER_ID.value() + ":opencode",
                binding(USER_ID, new OpencodeProcessId("ocp_missing_process"), "server-a", 4097));
        UserOpencodeProcessAssignmentService service = service(
                repository,
                new RecordingGateway(),
                "server-a",
                "192.168.100.165",
                heartbeatStore(List.of(backendSnapshot("bjp_selected", "server-a", "http://192.168.100.171:8080", NOW))));

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.NEEDS_INITIALIZATION);
        assertThat(response.linuxServerId()).isEqualTo("server-a");
        assertThat(response.port()).isEqualTo(4097);
        assertThat(response.serviceAddress()).isEqualTo("192.168.100.171:4097");
        assertThat(response.serviceAddress()).isNotEqualTo("server-a:4097");
    }

    @org.junit.jupiter.api.Test
    void allocationStatusKeepsServerNameAndDoesNotInventAddressWhenBackendIsUnavailable() {
        FakeRepository repository = new FakeRepository();
        repository.bindings.put(
                USER_ID.value() + ":opencode",
                binding(USER_ID, new OpencodeProcessId("ocp_existing"), "server-b", 4097));
        UserOpencodeProcessAssignmentService service = service(repository, new RecordingGateway(), "server-a", "192.168.100.165");

        UserOpencodeProcessStatusResponse response = service.allocationStatus(
                USER_ID,
                "opencode",
                "已分配 opencode 专属进程，但目标服务器后端不可用，暂无法确认进程健康状态",
                TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.UNAVAILABLE);
        assertThat(response.linuxServerId()).isEqualTo("server-b");
        assertThat(response.port()).isEqualTo(4097);
        assertThat(response.serviceAddress()).isNull();
    }

    @org.junit.jupiter.api.Test
    void staleProcessWithStableServerNameBaseUrlDoesNotInventAddressWhenBackendIsUnavailable() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess process = process(
                "ocp_existing",
                USER_ID,
                "server-a",
                "ctr_old",
                4097,
                OpencodeServerProcessStatus.UNHEALTHY);
        repository.processes.put(process.processId().value(), process);
        repository.bindings.put(USER_ID.value() + ":opencode", binding(USER_ID, process.processId(), "server-a", 4097));
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("down");
        UserOpencodeProcessAssignmentService service = service(repository, gateway, "server-b", "192.168.100.165");

        UserOpencodeProcessStatusResponse response = service.status(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.UNAVAILABLE);
        assertThat(response.linuxServerId()).isEqualTo("server-a");
        assertThat(response.port()).isEqualTo(4097);
        assertThat(response.baseUrl()).isEqualTo("http://server-a:4097");
        assertThat(response.serviceAddress()).isNull();
    }

    @org.junit.jupiter.api.Test
    void statusResponseDoesNotDeriveServiceAddressFromStableServerName() {
        UserOpencodeProcessStatusResponse response = new UserOpencodeProcessStatusResponse(
                UserOpencodeProcessAvailability.UNAVAILABLE,
                false,
                "目标服务器后端不可用",
                null,
                "server-a",
                null,
                4097,
                null,
                NOW,
                UserOpencodeServiceStatus.NOT_RUNNING,
                null,
                null);

        assertThat(response.serviceAddress()).isNull();
    }

    @org.junit.jupiter.api.Test
    void allocationStatusReadsActiveBindingWithoutGatewayHealth() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.12", 4096, 4100, 4, 1));
        repository.bindings.put(
                USER_ID.value() + ":opencode",
                binding(USER_ID, new OpencodeProcessId("ocp_existing"), "10.8.0.12", 4097));
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessStatusResponse response = service.allocationStatus(
                USER_ID,
                "opencode",
                "已分配 opencode 专属进程，但暂无法确认进程健康状态",
                TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.UNAVAILABLE);
        assertThat(response.initializable()).isFalse();
        assertThat(response.serviceStatus()).isEqualTo(UserOpencodeServiceStatus.NOT_RUNNING);
        assertThat(response.linuxServerId()).isEqualTo("10.8.0.12");
        assertThat(response.port()).isEqualTo(4097);
        assertThat(response.serviceAddress()).isNull();
        assertThat(response.message()).contains("已分配");
        assertThat(gateway.healthCommands).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void initializeDoesNotMigrateBindingToDifferentLinuxServerWhenOldServerHasNoContainer() {
        // 旧用户 binding 在 10.8.0.12 上，但该 IP 上已无可用容器；
        // 当前 Java 即使看到 10.8.0.13 有空闲容器，也不能静默迁移用户 binding。
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_other_linux", container("ctr_other_linux", "10.8.0.13", 4300, 4302, 3, 0));
        OpencodeServerProcess oldProcess = process("ocp_existing", USER_ID, "10.8.0.12", "ctr_old", 4096, OpencodeServerProcessStatus.UNHEALTHY);
        repository.processes.put(oldProcess.processId().value(), oldProcess);
        repository.bindings.put(USER_ID.value() + ":opencode", binding(USER_ID, oldProcess.processId(), "10.8.0.12", 4096));
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("down");
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        assertThatThrownBy(() -> service.initialize(USER_ID, "opencode", TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));
        assertThat(gateway.startCommands).isEmpty();
        assertThat(repository.findUserBinding(USER_ID, "opencode"))
                .get()
                .extracting(UserOpencodeProcessBinding::linuxServerId)
                .isEqualTo(new LinuxServerId("10.8.0.12"));
    }

    @org.junit.jupiter.api.Test
    void initializeDoesNotMigrateInactiveBindingToDifferentLinuxServer() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_current", container("ctr_current", "10.8.0.21", 4096, 4098, 3, 0));
        OpencodeProcessId oldProcessId = new OpencodeProcessId("ocp_inactive_old");
        repository.bindings.put(
                USER_ID.value() + ":opencode",
                inactiveBinding(USER_ID, oldProcessId, "10.8.0.12", 4096));
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        assertThatThrownBy(() -> service.initialize(USER_ID, "opencode", TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));
        assertThat(gateway.startCommands).isEmpty();
        assertThat(repository.findUserBinding(USER_ID, "opencode"))
                .get()
                .extracting(UserOpencodeProcessBinding::linuxServerId)
                .isEqualTo(new LinuxServerId("10.8.0.12"));
    }

    @org.junit.jupiter.api.Test
    void initializeDelegatesPublicConfigCheckToSelectedManagerWhenLocalDirIsMissing() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_busy", container("ctr_busy", "10.8.0.12", 4096, 4100, 4, 3));
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.13", 4200, 4205, 4, 0));
        RecordingGateway gateway = new RecordingGateway();
        Path missingConfigDir = tempDir.resolve("missing-opencode-config");
        UserOpencodeProcessAssignmentService service = serviceWithPublicConfigDir(
                repository,
                gateway,
                missingConfigDir);

        UserOpencodeProcessStatusResponse response = service.initialize(USER_ID, "opencode", TRACE_ID);

        assertThat(response.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        assertThat(response.baseUrl()).isEqualTo("http://10.8.0.21:4200");
        assertThat(gateway.startCommands).singleElement().satisfies(command -> {
            assertThat(command.containerId()).isEqualTo(new OpencodeContainerId("ctr_idle"));
            assertThat(command.linuxServerId()).isEqualTo(new LinuxServerId("10.8.0.13"));
            assertThat(command.configPath()).isEqualTo(missingConfigDir.toString().replace('\\', '/') + "/");
        });
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
    void initializeTriesNextCandidateOnlyWhenManagerCommandWasNotDispatched() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_a", container("ctr_a", "10.8.0.12", 4096, 4100, 4, 0));
        repository.containers.put("ctr_b", container("ctr_b", "10.8.0.13", 4200, 4205, 4, 1));
        RecordingGateway gateway = new RecordingGateway();
        gateway.startFailures.add(new ManagerCommandNotDispatchedException(new OpencodeContainerId("ctr_a")));
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessStatusResponse response = service.initialize(USER_ID, "opencode", TRACE_ID);

        assertThat(response.containerId()).isEqualTo("ctr_b");
        assertThat(gateway.startAttempts).extracting(command -> command.containerId().value())
                .containsExactly("ctr_a", "ctr_b");
        assertThat(repository.findUserBinding(USER_ID, "opencode")).get()
                .extracting(UserOpencodeProcessBinding::linuxServerId)
                .isEqualTo(new LinuxServerId("10.8.0.13"));
    }

    @org.junit.jupiter.api.Test
    void initializeDoesNotSwitchCandidateAfterManagerTimeout() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_a", container("ctr_a", "10.8.0.12", 4096, 4100, 4, 0));
        repository.containers.put("ctr_b", container("ctr_b", "10.8.0.13", 4200, 4205, 4, 1));
        RecordingGateway gateway = new RecordingGateway();
        gateway.startFailures.add(new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "manager command timeout"));
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        assertThatThrownBy(() -> service.initialize(USER_ID, "opencode", TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_TIMEOUT));

        assertThat(gateway.startAttempts).extracting(command -> command.containerId().value())
                .containsExactly("ctr_a");
    }

    @org.junit.jupiter.api.Test
    void initializeDoesNotSwitchCandidateWhenConnectionDropsDuringPostStartHealthCheck() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_a", container("ctr_a", "10.8.0.12", 4096, 4100, 4, 0));
        repository.containers.put("ctr_b", container("ctr_b", "10.8.0.13", 4200, 4205, 4, 1));
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthFailure = new ManagerCommandNotDispatchedException(new OpencodeContainerId("ctr_a"));
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        assertThatThrownBy(() -> service.initialize(USER_ID, "opencode", TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        assertThat(gateway.startAttempts).extracting(command -> command.containerId().value())
                .containsExactly("ctr_a");
    }

    @org.junit.jupiter.api.Test
    void initializeDoesNotReturnReadyWhenStartedProcessFailsHealth() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.12", 4096, 4100, 4, 0));
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("opencode http health failed");
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        assertThatThrownBy(() -> service.initialize(USER_ID, "opencode", TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        OpencodeServerProcess process = repository.processes.values().stream().findFirst().orElseThrow();
        assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.UNHEALTHY);
        assertThat(process.healthMessage()).isEqualTo("opencode http health failed");
        assertThat(repository.findUserBinding(USER_ID, "opencode")).isEmpty();
        assertThat(repository.savedNodes).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void fileRoutingAffinityReturnsBoundServerWithoutCallingGatewayHealth() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess process = process("ocp_existing", USER_ID, "10.8.0.12", "ctr_idle", 4096, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(process.processId().value(), process);
        repository.bindings.put(USER_ID.value() + ":opencode", binding(USER_ID, process.processId(), "10.8.0.12", 4096));
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthFailure = new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "opencode 管理进程命令超时");
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessFileRoutingAffinity affinity = service.fileRoutingAffinity(USER_ID, "opencode", TRACE_ID);

        assertThat(affinity.status()).isEqualTo(UserOpencodeProcessAvailability.READY);
        assertThat(affinity.processId()).isEqualTo("ocp_existing");
        assertThat(affinity.linuxServerId()).isEqualTo("10.8.0.12");
        assertThat(affinity.containerId()).isEqualTo("ctr_idle");
        assertThat(affinity.port()).isEqualTo(4096);
        assertThat(affinity.serviceAddress()).isEqualTo("10.8.0.12:4096");
        assertThat(gateway.healthCommands).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void routingLinuxServerIdReadsActiveBindingWithoutGatewayHealth() {
        FakeRepository repository = new FakeRepository();
        repository.bindings.put(
                USER_ID.value() + ":opencode",
                binding(USER_ID, new OpencodeProcessId("ocp_existing"), "10.8.0.12", 4096));
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthFailure = new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "不应触发健康检查");
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        Optional<String> target = service.routingLinuxServerId(USER_ID, "opencode");

        assertThat(target).contains("10.8.0.12");
        assertThat(gateway.healthCommands).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void fileRoutingAffinityReportsUnavailableWhenUserHasNoBinding() {
        FakeRepository repository = new FakeRepository();
        repository.containers.put("ctr_idle", container("ctr_idle", "10.8.0.12", 4096, 4100, 4, 0));
        RecordingGateway gateway = new RecordingGateway();
        UserOpencodeProcessAssignmentService service = service(repository, gateway);

        UserOpencodeProcessFileRoutingAffinity affinity = service.fileRoutingAffinity(USER_ID, "opencode", TRACE_ID);

        assertThat(affinity.status()).isEqualTo(UserOpencodeProcessAvailability.NEEDS_INITIALIZATION);
        assertThat(affinity.initializable()).isTrue();
        assertThat(affinity.linuxServerId()).isNull();
        assertThat(gateway.healthCommands).isEmpty();
    }

    private static UserOpencodeProcessAssignmentService service(FakeRepository repository, RecordingGateway gateway) {
        return service(repository, gateway, "10.8.0.21", "10.8.0.21");
    }

    private static UserOpencodeProcessAssignmentService serviceWithLiveContainers(
            FakeRepository repository,
            RecordingGateway gateway,
            List<OpencodeContainer> liveContainers) {
        return serviceWithPublicConfigDir(
                repository,
                gateway,
                Path.of(CONFIG_DIR),
                "10.8.0.21",
                "10.8.0.21",
                disabledHeartbeatStore(),
                null,
                liveContainers);
    }

    private static UserOpencodeProcessAssignmentService service(
            FakeRepository repository,
            RecordingGateway gateway,
            OpencodeProcessStartOperationRepository operationRepository) {
        return serviceWithPublicConfigDir(repository, gateway, Path.of(CONFIG_DIR), "10.8.0.21", "10.8.0.21", operationRepository);
    }

    private static UserOpencodeProcessAssignmentService service(
            FakeRepository repository,
            RecordingGateway gateway,
            String linuxServerId,
            String advertisedHost) {
        return service(repository, gateway, linuxServerId, advertisedHost, disabledHeartbeatStore());
    }

    private static UserOpencodeProcessAssignmentService service(
            FakeRepository repository,
            RecordingGateway gateway,
            String linuxServerId,
            String advertisedHost,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        return serviceWithPublicConfigDir(repository, gateway, Path.of(CONFIG_DIR), linuxServerId, advertisedHost, heartbeatStore);
    }

    private static UserOpencodeProcessAssignmentService serviceWithPublicConfigDir(
            FakeRepository repository,
            RecordingGateway gateway,
            Path publicConfigDir) {
        return serviceWithPublicConfigDir(repository, gateway, publicConfigDir, "10.8.0.21", "10.8.0.21");
    }

    private static UserOpencodeProcessAssignmentService serviceWithPublicConfigDir(
            FakeRepository repository,
            RecordingGateway gateway,
            Path publicConfigDir,
            String linuxServerId,
            String advertisedHost) {
        return serviceWithPublicConfigDir(repository, gateway, publicConfigDir, linuxServerId, advertisedHost, disabledHeartbeatStore(), null);
    }

    private static UserOpencodeProcessAssignmentService serviceWithPublicConfigDir(
            FakeRepository repository,
            RecordingGateway gateway,
            Path publicConfigDir,
            String linuxServerId,
            String advertisedHost,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        return serviceWithPublicConfigDir(repository, gateway, publicConfigDir, linuxServerId, advertisedHost, heartbeatStore, null);
    }

    private static UserOpencodeProcessAssignmentService serviceWithPublicConfigDir(
            FakeRepository repository,
            RecordingGateway gateway,
            Path publicConfigDir,
            String linuxServerId,
            String advertisedHost,
            OpencodeProcessStartOperationRepository operationRepository) {
        return serviceWithPublicConfigDir(repository, gateway, publicConfigDir, linuxServerId, advertisedHost, disabledHeartbeatStore(), operationRepository);
    }

    private static UserOpencodeProcessAssignmentService serviceWithPublicConfigDir(
            FakeRepository repository,
            RecordingGateway gateway,
            Path publicConfigDir,
            String linuxServerId,
            String advertisedHost,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStartOperationRepository operationRepository) {
        return serviceWithPublicConfigDir(
                repository,
                gateway,
                publicConfigDir,
                linuxServerId,
                advertisedHost,
                heartbeatStore,
                operationRepository,
                new ArrayList<>(repository.containers.values()));
    }

    private static UserOpencodeProcessAssignmentService serviceWithPublicConfigDir(
            FakeRepository repository,
            RecordingGateway gateway,
            Path publicConfigDir,
            String linuxServerId,
            String advertisedHost,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessStartOperationRepository operationRepository,
            List<OpencodeContainer> liveContainers) {
        BackendJavaProcessLifecycleService backendLifecycle = new BackendJavaProcessLifecycleService(
                repository,
                new ManagerControlSettings(
                        "secret-token",
                        "http://" + advertisedHost + ":8080",
                        new LinuxServerId(linuxServerId),
                        advertisedHost,
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(5),
                        100));
        ManagerConnectionRegistry connectionRegistry = new ManagerConnectionRegistry();
        List<ManagerRuntimeSnapshot> managerSnapshots = liveContainers.stream()
                .map(container -> managerSnapshot(container, backendLifecycle.backendProcessId()))
                .toList();
        for (ManagerRuntimeSnapshot snapshot : managerSnapshots) {
            connectionRegistry.register(
                    snapshot.manager().managerId(),
                    snapshot.container().containerId(),
                    backendLifecycle.backendProcessId(),
                    message -> { });
        }
        LiveOpencodeContainerCandidateResolver candidateResolver = new LiveOpencodeContainerCandidateResolver(
                heartbeatStoreWithManagerSnapshots(heartbeatStore, managerSnapshots),
                backendLifecycle,
                connectionRegistry);
        return new UserOpencodeProcessAssignmentService(
                repository,
                commonParameters(publicConfigDir),
                repository,
                gateway,
                backendLifecycle,
                heartbeatStore,
                candidateResolver,
                null,
                null,
                null,
                operationRepository,
                repository);
    }

    private static final String SESSION_DIR = "/tmp/testagent/.session/";
    private static final String USER_UNIFIED_AUTH_ID = "ucid_001";
    private static final String USER_SESSION_DIR = SESSION_DIR + "users/" + USER_UNIFIED_AUTH_ID;
    private static final String CONFIG_DIR = "/tmp/testagent/.config/opencode/";

    private static CommonParameterValues commonParameters() {
        return commonParameters(Path.of(CONFIG_DIR));
    }

    private static CommonParameterValues commonParameters(Path publicConfigDir) {
        Map<String, String> parameters = Map.of(
                "OPENCODE_SESSION_DIR", SESSION_DIR,
                "OPENCODE_PUBLIC_CONFIG_DIR", publicConfigDir.toString());
        return new CommonParameterValues() {
            @Override
            public Optional<String> resolvedValue(String englishName) {
                return Optional.ofNullable(parameters.get(englishName));
            }

            @Override
            public Optional<String> resolvedValue(String englishName, com.enterprise.testagent.domain.configuration.ParameterPlatform platform) {
                return Optional.ofNullable(parameters.get(englishName));
            }

            @Override
            public Optional<CommonParameter> raw(String englishName, com.enterprise.testagent.domain.configuration.ParameterPlatform platform) {
                return Optional.empty();
            }

            @Override
            public List<CommonParameter> findAll() {
                return List.of();
            }

            @Override
            public List<com.enterprise.testagent.domain.configuration.ResolvedParameter> resolvedAll() {
                return List.of();
            }
        };
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
                com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerStatus.READY,
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

    private static OpencodeServerProcess withLastHealthCheckAt(
            OpencodeServerProcess process,
            Instant lastHealthCheckAt) {
        return new OpencodeServerProcess(
                process.processId(),
                process.userId(),
                process.linuxServerId(),
                process.containerId(),
                process.port(),
                process.pid(),
                process.baseUrl(),
                process.status(),
                process.sessionPath(),
                process.configPath(),
                process.startedAt(),
                lastHealthCheckAt,
                process.healthMessage(),
                process.createdAt(),
                process.updatedAt(),
                process.traceId());
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

    private static UserOpencodeProcessBinding inactiveBinding(
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
                UserOpencodeProcessBindingStatus.INACTIVE,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static BackendRuntimeSnapshot backendSnapshot(
            String backendProcessId,
            String linuxServerId,
            String listenUrl,
            Instant heartbeatAt) {
        LinuxServerId serverId = new LinuxServerId(linuxServerId);
        return new BackendRuntimeSnapshot(
                new LinuxServer(
                        serverId,
                        linuxServerId,
                        LinuxServerStatus.READY,
                        Map.of("backendListenUrl", listenUrl),
                        heartbeatAt,
                        NOW.minusSeconds(60),
                        heartbeatAt,
                        TRACE_ID),
                new BackendJavaProcess(
                        new BackendProcessId(backendProcessId),
                        serverId,
                        listenUrl,
                        com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus.READY,
                        NOW.minusSeconds(60),
                        heartbeatAt,
                        NOW.minusSeconds(60),
                        heartbeatAt,
                        TRACE_ID));
    }

    private static ManagerRuntimeSnapshot managerSnapshot(
            OpencodeContainer container,
            BackendProcessId backendProcessId) {
        ContainerManagerId managerId = new ContainerManagerId("mgr_" + container.containerId().value());
        OpencodeContainerManager manager = new OpencodeContainerManager(
                managerId,
                container.containerId(),
                container.linuxServerId(),
                "1.0",
                ManagerConnectionStatus.CONNECTED,
                Map.of(),
                container.lastHeartbeatAt(),
                container.createdAt(),
                container.updatedAt(),
                TRACE_ID);
        OpencodeManagerBackendConnection connection = new OpencodeManagerBackendConnection(
                managerId,
                backendProcessId,
                ManagerConnectionStatus.CONNECTED,
                container.createdAt(),
                container.lastHeartbeatAt(),
                container.updatedAt(),
                TRACE_ID);
        return new ManagerRuntimeSnapshot(container, manager, List.of(connection));
    }

    private static OpencodeProcessHeartbeatStore heartbeatStoreWithManagerSnapshots(
            OpencodeProcessHeartbeatStore delegate,
            List<ManagerRuntimeSnapshot> managerSnapshots) {
        return new OpencodeProcessHeartbeatStore() {
            @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) {
                delegate.recordBackendHeartbeat(linuxServerId, heartbeatAt);
            }
            @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) {
                delegate.recordBackendSnapshot(snapshot);
            }
            @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) {
                delegate.recordManagerSnapshot(snapshot);
            }
            @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) {
                delegate.recordOpencodeHeartbeat(processId, heartbeatAt);
            }
            @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() {
                return delegate.liveBackendSnapshots();
            }
            @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() {
                return managerSnapshots;
            }
            @Override public Set<LinuxServerId> liveBackendServerIds() {
                return delegate.liveBackendServerIds();
            }
            @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() {
                return delegate.liveOpencodeProcessIds();
            }
            @Override public void cleanupExpiredHeartbeats() {
                delegate.cleanupExpiredHeartbeats();
            }
        };
    }

    private static OpencodeProcessHeartbeatStore heartbeatStore(List<BackendRuntimeSnapshot> backendSnapshots) {
        return new OpencodeProcessHeartbeatStore() {
            @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) { }
            @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) { }
            @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) { }
            @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { }
            @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return backendSnapshots; }
            @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
            @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
            @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
            @Override public void cleanupExpiredHeartbeats() { }
        };
    }

    private static OpencodeProcessHeartbeatStore disabledHeartbeatStore() {
        return heartbeatStore(List.of());
    }

    private static final class RecordingGateway implements OpencodeProcessManagerGateway {
        private final List<OpencodeProcessStartCommand> startCommands = new ArrayList<>();
        private final List<OpencodeProcessStartCommand> startAttempts = new ArrayList<>();
        private final List<OpencodeProcessHealthCommand> healthCommands = new ArrayList<>();
        private final Queue<OpencodeProcessHealthResult> healthResults = new ArrayDeque<>();
        private final Queue<RuntimeException> startFailures = new ArrayDeque<>();
        private OpencodeProcessHealthResult health = OpencodeProcessHealthResult.healthy("ok");
        private PlatformException healthFailure;
        private PlatformException startFailure;

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            healthCommands.add(command);
            if (healthFailure != null) {
                throw healthFailure;
            }
            return healthResults.isEmpty() ? health : healthResults.remove();
        }

        @Override
        public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) {
            startAttempts.add(command);
            if (!startFailures.isEmpty()) {
                throw startFailures.remove();
            }
            if (startFailure != null) {
                throw startFailure;
            }
            startCommands.add(command);
            return new OpencodeProcessStartResult(12345L, "started");
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

    static class RecordingStartOperationRepository implements OpencodeProcessStartOperationRepository {
        private final Map<String, OpencodeProcessStartOperation> operations = new LinkedHashMap<>();
        private final List<OpencodeProcessStartOperationStep> steps = new ArrayList<>();

        @Override
        public OpencodeProcessStartOperation start(
                String operationId,
                UserId requestedBy,
                String agentId,
                String traceId,
                Instant now) {
            OpencodeProcessStartOperation operation = new OpencodeProcessStartOperation(
                    operationId,
                    requestedBy,
                    agentId,
                    OpencodeProcessStartOperationStatus.RUNNING,
                    OpencodeProcessStartOperationStep.VALIDATING_REQUEST,
                    null,
                    null,
                    null,
                    null,
                    traceId,
                    now,
                    now);
            operations.put(operationId, operation);
            steps.add(operation.currentStep());
            return operation;
        }

        @Override
        public OpencodeProcessStartOperation markStep(String operationId, OpencodeProcessStartOperationStep step, Instant now) {
            OpencodeProcessStartOperation current = operations.get(operationId);
            OpencodeProcessStartOperation operation = new OpencodeProcessStartOperation(
                    current.operationId(),
                    current.requestedBy(),
                    current.agentId(),
                    OpencodeProcessStartOperationStatus.RUNNING,
                    step,
                    null,
                    null,
                    current.processId(),
                    current.serviceAddress(),
                    current.traceId(),
                    current.createdAt(),
                    now);
            operations.put(operationId, operation);
            steps.add(step);
            return operation;
        }

        @Override
        public OpencodeProcessStartOperation markSucceeded(
                String operationId,
                String processId,
                String serviceAddress,
                Instant now) {
            OpencodeProcessStartOperation current = operations.get(operationId);
            OpencodeProcessStartOperation operation = new OpencodeProcessStartOperation(
                    current.operationId(),
                    current.requestedBy(),
                    current.agentId(),
                    OpencodeProcessStartOperationStatus.SUCCEEDED,
                    OpencodeProcessStartOperationStep.COMPLETED,
                    null,
                    null,
                    processId,
                    serviceAddress,
                    current.traceId(),
                    current.createdAt(),
                    now);
            operations.put(operationId, operation);
            steps.add(OpencodeProcessStartOperationStep.COMPLETED);
            return operation;
        }

        @Override
        public OpencodeProcessStartOperation markFailed(
                String operationId,
                OpencodeProcessStartOperationStep step,
                String errorCode,
                String errorMessage,
                Instant now) {
            OpencodeProcessStartOperation current = operations.get(operationId);
            OpencodeProcessStartOperation operation = new OpencodeProcessStartOperation(
                    current.operationId(),
                    current.requestedBy(),
                    current.agentId(),
                    OpencodeProcessStartOperationStatus.FAILED,
                    step,
                    errorCode,
                    errorMessage,
                    current.processId(),
                    current.serviceAddress(),
                    current.traceId(),
                    current.createdAt(),
                    now);
            operations.put(operationId, operation);
            steps.add(step);
            return operation;
        }

        @Override
        public Optional<OpencodeProcessStartOperation> findById(String operationId, UserId requestedBy) {
            return Optional.ofNullable(operations.get(operationId))
                    .filter(operation -> operation.requestedBy().equals(requestedBy));
        }
    }

    static class FakeRepository implements OpencodeProcessManagementRepository, ExecutionNodeRepository, UserRepository {
        private final Map<String, OpencodeContainer> containers = new LinkedHashMap<>();
        private final Map<String, OpencodeServerProcess> processes = new LinkedHashMap<>();
        private final Map<String, UserOpencodeProcessBinding> bindings = new LinkedHashMap<>();
        private final Map<String, User> users = new LinkedHashMap<>();
        private final List<ExecutionNode> savedNodes = new ArrayList<>();
        int findUserBindingCalls;

        FakeRepository() {
            users.put(USER_ID.value(), User.createNew(
                    USER_ID.value(),
                    USER_UNIFIED_AUTH_ID,
                    "test-user",
                    "password-hash",
                    "org",
                    "rd",
                    "dept"));
        }

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
        public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) {
            return processes.values().stream()
                    .filter(process -> process.linuxServerId().equals(linuxServerId))
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

        @Override
        public void save(User user) {
            users.put(user.userId().value(), user);
        }

        @Override
        public Optional<User> findByUserId(UserId userId) {
            return Optional.ofNullable(users.get(userId.value()));
        }

        @Override
        public Optional<User> findByUnifiedAuthId(String unifiedAuthId) {
            return users.values().stream()
                    .filter(user -> user.unifiedAuthId().equals(unifiedAuthId))
                    .findFirst();
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return users.values().stream()
                    .filter(user -> user.username().equals(username))
                    .findFirst();
        }

        @Override
        public PageResponse<User> findPage(String keyword, PageRequest pageRequest) {
            return new PageResponse<>(List.copyOf(users.values()), pageRequest.page(), pageRequest.size(), users.size());
        }

        @Override
        public boolean existsByUsername(String username) {
            return findByUsername(username).isPresent();
        }

        @Override
        public boolean existsByUnifiedAuthId(String unifiedAuthId) {
            return findByUnifiedAuthId(unifiedAuthId).isPresent();
        }

        @Override public LinuxServer saveLinuxServer(LinuxServer linuxServer) { return linuxServer; }
        @Override public Optional<LinuxServer> findLinuxServerById(LinuxServerId linuxServerId) { return Optional.empty(); }
        @Override public BackendJavaProcess saveBackendJavaProcess(BackendJavaProcess backendJavaProcess) { return backendJavaProcess; }
        @Override public Optional<BackendJavaProcess> findBackendJavaProcessById(BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public Optional<BackendJavaProcess> findReadyBackendJavaProcessByLinuxServer(LinuxServerId linuxServerId) { return Optional.empty(); }
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

}
