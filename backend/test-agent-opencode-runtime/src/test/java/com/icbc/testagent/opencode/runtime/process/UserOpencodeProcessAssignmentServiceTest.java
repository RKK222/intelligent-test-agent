package com.icbc.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterValues;
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
        assertThat(gateway.healthCommands).hasSize(1);
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
        assertThat(gateway.startCommands.getFirst().baseUrl()).isEqualTo("http://10.8.0.12:4097");
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
        assertThat(response.serviceAddress()).isEqualTo("10.8.0.12:4096");
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
        assertThat(response.serviceAddress()).isEqualTo("10.8.0.12:4097");
        assertThat(response.message()).contains("已分配");
        assertThat(gateway.healthCommands).isEmpty();
        assertThat(repository.findContainerCalls).isZero();
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
        assertThat(response.baseUrl()).isEqualTo("http://10.8.0.13:4200");
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
        assertThat(repository.findContainerCalls).isEqualTo(1);
    }

    private static UserOpencodeProcessAssignmentService service(FakeRepository repository, RecordingGateway gateway) {
        return serviceWithPublicConfigDir(repository, gateway, Path.of(CONFIG_DIR));
    }

    private static UserOpencodeProcessAssignmentService serviceWithPublicConfigDir(
            FakeRepository repository,
            RecordingGateway gateway,
            Path publicConfigDir) {
        return new UserOpencodeProcessAssignmentService(
                repository,
                commonParameters(publicConfigDir),
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
            public Optional<String> resolvedValue(String englishName, com.icbc.testagent.domain.configuration.ParameterPlatform platform) {
                return Optional.ofNullable(parameters.get(englishName));
            }

            @Override
            public Optional<CommonParameter> raw(String englishName, com.icbc.testagent.domain.configuration.ParameterPlatform platform) {
                return Optional.empty();
            }

            @Override
            public List<CommonParameter> findAll() {
                return List.of();
            }

            @Override
            public List<com.icbc.testagent.domain.configuration.ResolvedParameter> resolvedAll() {
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

    private static final class RecordingGateway implements OpencodeProcessManagerGateway {
        private final List<OpencodeProcessStartCommand> startCommands = new ArrayList<>();
        private final List<OpencodeProcessHealthCommand> healthCommands = new ArrayList<>();
        private final Queue<OpencodeProcessHealthResult> healthResults = new ArrayDeque<>();
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

}
