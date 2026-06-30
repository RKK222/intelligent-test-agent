package com.icbc.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpencodeProcessStopServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void stopTrackedProcessStopsThroughGatewayThenConfirmsHealthIsGone() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("process not found");
        OpencodeProcessStopService service = service(repository, gateway);

        OpencodeProcessControlResult result = service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID));

        assertThat(gateway.stopCommands).singleElement().satisfies(command -> {
            assertThat(command.containerId()).isEqualTo(running.containerId());
            assertThat(command.port()).isEqualTo(running.port());
        });
        assertThat(gateway.healthCommands).singleElement().satisfies(command -> {
            assertThat(command.processId()).isEqualTo(running.processId());
            assertThat(command.baseUrl()).isEqualTo(running.baseUrl());
        });
        assertThat(result.command()).isEqualTo("stop");
        assertThat(result.status()).isEqualTo("STOPPED");
        assertThat(result.healthy()).isFalse();
        assertThat(result.pid()).isNull();
        assertThat(repository.findOpencodeServerProcessById(running.processId())).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.STOPPED);
            assertThat(process.pid()).isNull();
            assertThat(process.healthMessage()).isEqualTo("process not found");
        });
    }

    @Test
    void stopTrackedProcessDoesNotReturnSuccessWhenHealthStillHealthy() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.healthy("ok");
        OpencodeProcessStopService service = service(repository, gateway);

        assertThatThrownBy(() -> service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_BAD_GATEWAY));

        assertThat(repository.findOpencodeServerProcessById(running.processId())).get()
                .extracting(OpencodeServerProcess::status)
                .isEqualTo(OpencodeServerProcessStatus.RUNNING);
    }

    @Test
    void stopUntrackedPortOnlyUsesManagerStopResult() {
        RecordingGateway gateway = new RecordingGateway();
        OpencodeProcessStopService service = new OpencodeProcessStopService(gateway);

        OpencodeProcessControlResult result = service.stopAndVerify(OpencodeProcessStopRequest.untracked(
                new OpencodeContainerId("ctr_01"),
                4097,
                TRACE_ID));

        assertThat(gateway.stopCommands).hasSize(1);
        assertThat(gateway.healthCommands).isEmpty();
        assertThat(result.status()).isEqualTo("STOPPED");
    }

    private static OpencodeProcessStopService service(FakeRepository repository, RecordingGateway gateway) {
        return new OpencodeProcessStopService(gateway, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OpencodeServerProcess process(String processId, int port, OpencodeServerProcessStatus status) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId),
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                port,
                11111L,
                "http://10.8.0.12:" + port,
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
        private final List<OpencodeProcessControlCommand> stopCommands = new ArrayList<>();
        private final List<OpencodeProcessHealthCommand> healthCommands = new ArrayList<>();
        private OpencodeProcessHealthResult health = OpencodeProcessHealthResult.unhealthy("process not found");

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            healthCommands.add(command);
            return health;
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
            stopCommands.add(command);
            return new OpencodeProcessControlResult(
                    "stop",
                    "STOPPED",
                    command.port(),
                    11111L,
                    "http://10.8.0.12:" + command.port(),
                    "/data/opencode/session/" + command.port(),
                    "/data/opencode/.config/opencode/",
                    true,
                    "opencode server stopped",
                    command.traceId());
        }
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository {
        private final Map<OpencodeProcessId, OpencodeServerProcess> processes = new LinkedHashMap<>();

        @Override public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) { processes.put(process.processId(), process); return process; }
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
