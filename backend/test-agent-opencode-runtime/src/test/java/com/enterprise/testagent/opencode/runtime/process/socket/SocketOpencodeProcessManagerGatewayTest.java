package com.enterprise.testagent.opencode.runtime.process.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessControlCommand;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessHealthCommand;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessHealthResult;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessOwnedStopCommand;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartCommand;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartRejectionException;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartRejectionReason;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartResult;
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
                    assertThat(message.environment()).containsEntry("ENTERPRISE_UCID", "U001");
                    assertThat(message.unifiedAuthId()).isEqualTo("ucid_001");
                    assertThat(message.sessionPath()).isEqualTo("/data/opencode/session/users/ucid_001");
                    assertThat(message.bindingRecovery()).isTrue();
                    pending.complete(message.commandId(), commandResultWithProcessCreated(message, true));
                });
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        OpencodeProcessStartResult result = gateway.startProcess(new OpencodeProcessStartCommand(
                new UserId("usr_1234567890abcdef"),
                " ucid_001 ",
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4096,
                "http://10.8.0.12:4096",
                "/data/opencode/session/users/ucid_001",
                "/data/opencode/.config/opencode/",
                Map.of("ENTERPRISE_UCID", "U001"),
                "trace_1234567890abcdef",
                true));

        assertThat(result.pid()).isEqualTo(12345L);
        assertThat(result.message()).isEqualTo("started");
        assertThat(result.processCreated()).isTrue();
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
                "ucid_001",
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
    void startPreservesKnownManagerRejectionReasonWithoutExposingManagerMessage() {
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
                        "raw identity must not escape",
                        "PORT_CONFLICT",
                        message.traceId())));
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        assertThatThrownBy(() -> gateway.startProcess(startCommand()))
                .isInstanceOfSatisfying(OpencodeProcessStartRejectionException.class, exception -> {
                    assertThat(exception.reason()).isEqualTo(OpencodeProcessStartRejectionReason.PORT_CONFLICT);
                    assertThat(exception.getMessage()).doesNotContain("raw identity");
                });
    }

    @Test
    void startKeepsUnknownManagerFailureAsBadGateway() {
        FakeRepository repository = new FakeRepository();
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                        message.commandId(), message.command(), "FAILED", message.port(), null,
                        null, null, null, false, "manager failed", "SOMETHING_NEW", message.traceId())));
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        assertThatThrownBy(() -> gateway.startProcess(startCommand()))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_BAD_GATEWAY);
                    assertThat(exception.getMessage()).doesNotContain("manager failed");
                });
    }

    @Test
    void unhealthyHealthResultPreservesManagedPid() {
        FakeRepository repository = new FakeRepository();
        repository.process = process();
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                        message.commandId(), message.command(), "UNHEALTHY", message.port(), 12345L,
                        null, null, null, false, "http health failed", message.traceId())));
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        var result = gateway.checkHealth(new OpencodeProcessHealthCommand(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                "http://10.8.0.12:4096",
                "trace_1234567890abcdef"));

        assertThat(result.managerProcessPresent()).isTrue();
        assertThat(result.pid()).isEqualTo(12345L);
    }

    @Test
    void failedHealthMapsOnlyStableProcessNotManagedCodeToNotRunning() {
        FakeRepository repository = new FakeRepository();
        repository.process = process();
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                        message.commandId(), message.command(), "FAILED", message.port(), null,
                        null, null, null, false, "port is not managed", "PROCESS_NOT_MANAGED", message.traceId())));
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        OpencodeProcessHealthResult result = gateway.checkHealth(new OpencodeProcessHealthCommand(
                new OpencodeProcessId("ocp_1234567890abcdef"),
                "http://10.8.0.12:4096",
                "trace_1234567890abcdef"));

        assertThat(result.managerProcessPresent()).isFalse();
        assertThat(result.healthy()).isFalse();
    }

    @Test
    void failedHealthWithoutStableCodeFailsClosed() {
        FakeRepository repository = new FakeRepository();
        repository.process = process();
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                        message.commandId(), message.command(), "FAILED", message.port(), null,
                        null, null, null, false, "state store read failed", null, message.traceId())));
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        assertThatThrownBy(() -> gateway.checkHealth(new OpencodeProcessHealthCommand(
                        new OpencodeProcessId("ocp_1234567890abcdef"),
                        "http://10.8.0.12:4096",
                        "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_BAD_GATEWAY);
                    assertThat(exception.getMessage()).doesNotContain("state store read failed");
                });
    }

    @Test
    void unhealthyHealthWithoutManagedPidFailsClosed() {
        FakeRepository repository = new FakeRepository();
        repository.process = process();
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                        message.commandId(), message.command(), "UNHEALTHY", message.port(), null,
                        null, null, null, false, "pid unavailable", message.traceId())));
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        assertThatThrownBy(() -> gateway.checkHealth(new OpencodeProcessHealthCommand(
                        new OpencodeProcessId("ocp_1234567890abcdef"),
                        "http://10.8.0.12:4096",
                        "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_BAD_GATEWAY));
    }

    @Test
    void healthyHealthWithoutManagedPidFailsClosed() {
        FakeRepository repository = new FakeRepository();
        repository.process = process();
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        registry.register(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                        message.commandId(), message.command(), "HEALTHY", message.port(), null,
                        null, null, null, true, "pid unavailable", message.traceId())));
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        assertThatThrownBy(() -> gateway.checkHealth(new OpencodeProcessHealthCommand(
                        new OpencodeProcessId("ocp_1234567890abcdef"),
                        "http://10.8.0.12:4096",
                        "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_BAD_GATEWAY));
    }

    @Test
    void exactHealthSnapshotDoesNotRerouteThroughNewRepositoryAssignment() {
        FakeRepository repository = new FakeRepository();
        repository.process = new OpencodeServerProcess(
                process().processId(),
                process().userId(),
                process().linuxServerId(),
                new OpencodeContainerId("ctr_new"),
                4200,
                process().pid(),
                "http://10.8.0.12:4200",
                process().status(),
                process().sessionPath(),
                process().configPath(),
                process().startedAt(),
                process().lastHealthCheckAt(),
                process().healthMessage(),
                process().createdAt(),
                process().updatedAt(),
                process().traceId());
        ManagerConnectionRegistry registry = new ManagerConnectionRegistry();
        ManagerPendingCommandRegistry pending = new ManagerPendingCommandRegistry();
        List<ManagerControlMessage> oldCommands = new java.util.ArrayList<>();
        registry.register(
                new ContainerManagerId("mgr_old_1234567890"),
                new OpencodeContainerId("ctr_01"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> {
                    oldCommands.add(message);
                    pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                            message.commandId(), message.command(), "FAILED", message.port(), null,
                            null, null, null, false, "not managed", "PROCESS_NOT_MANAGED", message.traceId()));
                });
        registry.register(
                new ContainerManagerId("mgr_new_1234567890"),
                new OpencodeContainerId("ctr_new"),
                new BackendProcessId("bjp_1234567890abcdef"),
                message -> pending.complete(message.commandId(), ManagerControlMessage.commandResult(
                        message.commandId(), message.command(), "HEALTHY", message.port(), 12345L,
                        null, null, null, true, "new process", message.traceId())));
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        OpencodeProcessHealthResult result = gateway.checkHealth(new OpencodeProcessHealthCommand(
                repository.process.processId(),
                new OpencodeContainerId("ctr_01"),
                4096,
                "http://10.8.0.12:4096",
                "trace_1234567890abcdef"));

        assertThat(result.managerProcessPresent()).isFalse();
        assertThat(oldCommands).singleElement().extracting(ManagerControlMessage::port).isEqualTo(4096);
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
    void ownedStopSendsIdentityAndPidWithoutLegacyFallback() {
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
                            message.commandId(), message.command(), "STOPPED", message.port(), message.pid(),
                            null, null, null, false, "stopped", message.traceId()));
                });
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        var result = gateway.stopOwnedProcess(new OpencodeProcessOwnedStopCommand(
                new OpencodeContainerId("ctr_01"),
                4096,
                "ucid_001",
                12345L,
                "trace_1234567890abcdef"));

        assertThat(sent).singleElement().satisfies(message -> {
            assertThat(message.command()).isEqualTo("stopOwned");
            assertThat(message.port()).isEqualTo(4096);
            assertThat(message.unifiedAuthId()).isEqualTo("ucid_001");
            assertThat(message.pid()).isEqualTo(12345L);
        });
        assertThat(result.status()).isEqualTo("STOPPED");
    }

    @Test
    void ownedStopFailsClosedWhenLegacyManagerDoesNotKnowCommand() {
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
                            message.commandId(), message.command(), "FAILED", message.port(), null,
                            null, null, null, false, "unknown command", message.traceId()));
                });
        SocketOpencodeProcessManagerGateway gateway = gateway(repository, registry, pending);

        assertThatThrownBy(() -> gateway.stopOwnedProcess(new OpencodeProcessOwnedStopCommand(
                        new OpencodeContainerId("ctr_01"), 4096, "ucid_001", 12345L,
                        "trace_1234567890abcdef")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_BAD_GATEWAY);
                    assertThat(exception.getMessage()).doesNotContain("unknown command");
                });
        assertThat(sent).singleElement().extracting(ManagerControlMessage::command).isEqualTo("stopOwned");
    }

    @Test
    void startFailsWhenNoManagerConnectionExists() {
        SocketOpencodeProcessManagerGateway gateway = gateway(
                new FakeRepository(),
                new ManagerConnectionRegistry(),
                new ManagerPendingCommandRegistry());

        assertThatThrownBy(() -> gateway.startProcess(new OpencodeProcessStartCommand(
                new UserId("usr_1234567890abcdef"),
                "ucid_001",
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4096,
                "http://10.8.0.12:4096",
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                Map.of(),
                "trace_1234567890abcdef")))
                .isInstanceOf(ManagerCommandNotDispatchedException.class)
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

    private static OpencodeProcessStartCommand startCommand() {
        return new OpencodeProcessStartCommand(
                new UserId("usr_1234567890abcdef"),
                "ucid_001",
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4096,
                "http://10.8.0.12:4096",
                "/data/opencode/session/users/ucid_001",
                "/data/opencode/.config/opencode/",
                Map.of(),
                "trace_1234567890abcdef");
    }

    private static ManagerControlMessage commandResultWithProcessCreated(
            ManagerControlMessage command,
            boolean processCreated) {
        return new ManagerControlMessageCodec(new com.fasterxml.jackson.databind.ObjectMapper()).decode("""
                {
                  "type":"commandResult",
                  "protocolVersion":"opencode-manager.v1",
                  "traceId":"%s",
                  "commandId":"%s",
                  "command":"%s",
                  "port":%d,
                  "status":"STARTED",
                  "pid":12345,
                  "baseUrl":"http://10.8.0.12:4096",
                  "sessionPath":"/data/opencode/session/4096",
                  "configPath":"/data/opencode/.config/opencode/",
                  "healthy":true,
                  "processCreated":%s,
                  "message":"started"
                }
                """.formatted(
                command.traceId(),
                command.commandId(),
                command.command(),
                command.port(),
                processCreated));
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
        @Override public Optional<BackendJavaProcess> findReadyBackendJavaProcessByLinuxServer(LinuxServerId linuxServerId) { return Optional.empty(); }
        @Override public List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit) { return List.of(); }
        @Override public OpencodeContainer saveContainer(OpencodeContainer container) { return container; }
        @Override public Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId) { return Optional.empty(); }
        @Override public List<OpencodeContainer> findHealthyContainers(int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit) { return List.of(); }
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
