package com.icbc.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessControlCommand;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessHealthCommand;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStartCommand;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStartResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SocketOpencodeProcessManagerGatewayTest {

    @Test
    void startSendsCommandAndMapsResult() {
        FakeRepository repository = new FakeRepository();
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> {
                    assertThat(message.environment()).containsEntry("ICBC_UCID", "U001");
                    pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                        message.commandId(),
                        message.command(),
                        "STARTED",
                        message.port(),
                        12345L,
                        "http://10.8.0.12:4096",
                        "/data/opencode/session/4096",
                        "/data/opencode/.config/opencode/",
                        true,
                        "started",
                        message.traceId()));
                });
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        OpencodeProcessStartResult result = gateway.startProcess(new OpencodeProcessStartCommand(
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4096,
                "http://10.8.0.12:4096",
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                Map.of("ICBC_UCID", "U001"),
                "trace_1234567890abcdef"));

        assertThat(result.pid()).isEqualTo(12345L);
        assertThat(result.message()).isEqualTo("started");
    }

    @Test
    void startMapsManagerOpencodeUnavailableErrorCode() {
        FakeRepository repository = new FakeRepository();
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                        message.commandId(),
                        message.command(),
                        "FAILED",
                        message.port(),
                        null,
                        null,
                        null,
                        null,
                        false,
                        "服务器10.8.0.12，公共 opencode 配置目录/data/opencode/.config/opencode/尚未初始化。请联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化后重试。",
                        "OPENCODE_UNAVAILABLE",
                        message.traceId())));
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        assertThatThrownBy(() -> gateway.startProcess(new OpencodeProcessStartCommand(
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4096,
                "http://10.8.0.12:4096",
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                Map.of(),
                "trace_1234567890abcdef")))
                .isInstanceOf(PlatformException.class)
                .satisfies(error -> {
                    PlatformException exception = (PlatformException) error;
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo(
                            "服务器10.8.0.12，公共 opencode 配置目录/data/opencode/.config/opencode/尚未初始化。请联系超级管理员进入“系统管理 → 配置管理 → opencode公共配置管理”完成初始化后重试。");
                });
    }

    @Test
    void healthRoutesByPersistedProcessContainer() {
        FakeRepository repository = new FakeRepository();
        repository.process = process();
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                        message.commandId(),
                        message.command(),
                        "HEALTHY",
                        message.port(),
                        12345L,
                        null,
                        null,
                        null,
                        true,
                        "ok",
                        message.traceId())));
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        assertThat(gateway.checkHealth(new OpencodeProcessHealthCommand(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                "http://10.8.0.12:4096",
                "trace_1234567890abcdef")).healthy()).isTrue();
    }

    @Test
    void restartSendsManagerCommandAndMapsStartedResult() {
        FakeRepository repository = new FakeRepository();
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        List<ManagerControlMessage> sent = new java.util.ArrayList<>();
        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> {
                    sent.add(message);
                    pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                            message.commandId(),
                            message.command(),
                            "STARTED",
                            message.port(),
                            12346L,
                            "http://10.8.0.12:4096",
                            "/data/opencode/session/4096",
                            "/data/opencode/.config/opencode/",
                            true,
                            "opencode server started",
                            message.traceId()));
                });
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        var result = gateway.restartProcess(new OpencodeProcessControlCommand(
                new OpencodeContainerId("ctr_01"),
                4096,
                "trace_1234567890abcdef"));

        assertThat(sent).singleElement().satisfies(message -> {
            assertThat(message.command()).isEqualTo("restart");
            assertThat(message.port()).isEqualTo(4096);
        });
        assertThat(result.status()).isEqualTo("STARTED");
        assertThat(result.pid()).isEqualTo(12346L);
    }

    @Test
    void stopSendsManagerCommandAndMapsStoppedResult() {
        FakeRepository repository = new FakeRepository();
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        List<ManagerControlMessage> sent = new java.util.ArrayList<>();
        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> {
                    sent.add(message);
                    pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                            message.commandId(),
                            message.command(),
                            "STOPPED",
                            message.port(),
                            12345L,
                            "http://10.8.0.12:4096",
                            "/data/opencode/session/4096",
                            "/data/opencode/.config/opencode/",
                            true,
                            "opencode server stopped",
                            message.traceId()));
                });
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        var result = gateway.stopProcess(new OpencodeProcessControlCommand(
                new OpencodeContainerId("ctr_01"),
                4096,
                "trace_1234567890abcdef"));

        assertThat(sent).singleElement().satisfies(message -> {
            assertThat(message.command()).isEqualTo("stop");
            assertThat(message.port()).isEqualTo(4096);
        });
        assertThat(result.status()).isEqualTo("STOPPED");
        assertThat(result.message()).isEqualTo("opencode server stopped");
    }

    @Test
    void startFailsWhenNoManagerConnectionExists() {
        SocketOpencodeProcessManagerGateway gateway = gateway(
                new FakeRepository(),
                new ManagerConnectionRegistry(),
                new ManagerPendingCommandRegistry());

        assertThatThrownBy(() -> gateway.startProcess(new OpencodeProcessStartCommand(
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4096,
                "http://10.8.0.12:4096",
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                Map.of(),
                "trace_1234567890abcdef")))
                .isInstanceOf(PlatformException.class)
                .extracting(error -> ((PlatformException) error).errorCode())
                .isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
    }

    private static SocketOpencodeProcessManagerGateway gateway(
            FakeRepository repository,
            ManagerConnectionRegistry registry,
            ManagerPendingCommandRegistry pending) {
        return new SocketOpencodeProcessManagerGateway(
                repository,
                registry,
                pending,
                settings());
    }

    private static ManagerControlSettings settings() {
        return new ManagerControlSettings(
                "secret-token",
                "http://10.8.0.21:8080",
                new LinuxServerId("10.8.0.21"),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                100);
    }

    private static OpencodeServerProcess process() {
        Instant now = Instant.parse("2026-06-24T00:00:00Z");
        return new OpencodeServerProcess(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4096,
                12345L,
                "http://10.8.0.12:4096",
                OpencodeServerProcessStatus.RUNNING,
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                now,
                now,
                "ok",
                now,
                now,
                "trace_1234567890abcdef");
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository {
        private OpencodeServerProcess process;

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
        @Override public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) { this.process = process; return process; }
        @Override public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) { return Optional.ofNullable(process); }
        @Override public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) { return List.of(); }
        @Override public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) { return binding; }
        @Override public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) { return Optional.empty(); }
        @Override public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return List.of(); }
    }
}
