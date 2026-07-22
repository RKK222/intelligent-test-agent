package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
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
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OpencodeProcessStopServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");
    private static final String TRACE_ID = "trace_1234567890abcdef";
    private static final String UNIFIED_AUTH_ID = "ucid_001";

    @Test
    void stopTrackedProcessPreflightsFreshPidThenStopsExactOwnedInstance() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.healthy(22222L, "ok"));
        gateway.healthResults.add(OpencodeProcessHealthResult.notRunning("process not found"));
        OpencodeProcessStopService service = service(repository, gateway);

        OpencodeProcessControlResult result = service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID));

        assertThat(gateway.ownedStopCommands).singleElement().satisfies(command -> {
            assertThat(command.containerId()).isEqualTo(running.containerId());
            assertThat(command.port()).isEqualTo(running.port());
            assertThat(command.expectedUnifiedAuthId()).isEqualTo(UNIFIED_AUTH_ID);
            assertThat(command.expectedPid()).isEqualTo(22222L);
        });
        assertThat(gateway.stopCommands).isEmpty();
        assertThat(gateway.healthCommands).hasSize(2).allSatisfy(command -> {
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
    void stopTrackedUnhealthyProcessUsesFreshManagerPidAndPersistsFromDatabaseGeneration() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.managedUnhealthy(
                22222L,
                "opencode http health failed"));
        gateway.healthResults.add(OpencodeProcessHealthResult.notRunning("process not found"));
        OpencodeProcessStopService service = service(repository, gateway);

        OpencodeProcessControlResult result = service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID));

        assertThat(gateway.ownedStopCommands).singleElement().satisfies(command ->
                assertThat(command.expectedPid()).isEqualTo(22222L));
        assertThat(result.status()).isEqualTo("STOPPED");
        assertThat(repository.findOpencodeServerProcessById(running.processId())).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.STOPPED);
            assertThat(process.pid()).isNull();
        });
    }

    @Test
    void staleTrackedStopCannotAdoptSameCoordinateNewLifecyclePid() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess stale = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        OpencodeServerProcess newLifecycle = new OpencodeServerProcess(
                stale.processId(),
                stale.userId(),
                stale.linuxServerId(),
                stale.containerId(),
                stale.port(),
                22222L,
                stale.baseUrl(),
                OpencodeServerProcessStatus.RUNNING,
                stale.sessionPath(),
                stale.configPath(),
                NOW.plusSeconds(1),
                NOW.plusSeconds(1),
                "new lifecycle running",
                stale.createdAt(),
                NOW.plusSeconds(1),
                "trace_new_lifecycle");
        repository.processes.put(newLifecycle.processId(), newLifecycle);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.managedUnhealthy(
                22222L,
                "opencode http health failed"));
        OpencodeProcessStopService service = service(repository, gateway);

        assertThatThrownBy(() -> service.stopAndVerify(OpencodeProcessStopRequest.tracked(stale, TRACE_ID)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        assertThat(gateway.healthCommands).isEmpty();
        assertThat(gateway.ownedStopCommands).isEmpty();
        assertThat(repository.findOpencodeServerProcessById(stale.processId())).contains(newLifecycle);
    }

    @Test
    void successfulTrackedStopInvalidatesProcessConversationContexts() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.healthy(11111L, "ok"));
        gateway.healthResults.add(OpencodeProcessHealthResult.notRunning("process not found"));
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        OpencodeProcessStopService service = new OpencodeProcessStopService(
                gateway,
                repository,
                null,
                userRepository(),
                contextStore,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID));

        Mockito.verify(contextStore).invalidateProcess(running.processId().value());
    }

    @Test
    void stopTrackedProcessDoesNotReturnSuccessWhenHealthStillHealthy() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.healthy(11111L, "ok"));
        gateway.healthResults.add(OpencodeProcessHealthResult.healthy(11111L, "still running"));
        OpencodeProcessStopService service = service(repository, gateway);

        assertThatThrownBy(() -> service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_BAD_GATEWAY));

        assertThat(repository.findOpencodeServerProcessById(running.processId())).get()
                .extracting(OpencodeServerProcess::status)
                .isEqualTo(OpencodeServerProcessStatus.RUNNING);
    }

    @Test
    void stopTrackedProcessDoesNotReturnSuccessWhenHealthCheckStillFails() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.healthy(11111L, "ok"));
        gateway.healthResults.add(OpencodeProcessHealthResult.managedUnhealthy(
                11111L,
                "opencode http health failed"));
        OpencodeProcessStopService service = service(repository, gateway);

        assertThatThrownBy(() -> service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_BAD_GATEWAY));

        assertThat(repository.findOpencodeServerProcessById(running.processId())).get()
                .extracting(OpencodeServerProcess::status)
                .isEqualTo(OpencodeServerProcessStatus.RUNNING);
    }

    @Test
    void stopTrackedProcessFailsClosedWhenPreflightIsStale() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthFailure = new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "manager timeout");
        OpencodeProcessStopService service = service(repository, gateway);

        assertThatThrownBy(() -> service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_TIMEOUT));

        assertThat(gateway.ownedStopCommands).isEmpty();
        assertThat(gateway.stopCommands).isEmpty();
    }

    @Test
    void stopTrackedProcessReturnsAlreadyStoppedWithoutSendingStop() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.notRunning("process not found"));
        OpencodeProcessStopService service = service(repository, gateway);

        OpencodeProcessControlResult result = service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID));

        assertThat(result.status()).isEqualTo("STOPPED");
        assertThat(gateway.ownedStopCommands).isEmpty();
        assertThat(gateway.stopCommands).isEmpty();
        assertThat(repository.findOpencodeServerProcessById(running.processId())).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.STOPPED);
            assertThat(process.pid()).isNull();
        });
    }

    @Test
    void stopTrackedProcessFailsClosedWhenPidCannotBeConfirmed() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process(
                "ocp_running",
                4097,
                null,
                OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.healthy("ok"));
        OpencodeProcessStopService service = service(repository, gateway);

        assertThatThrownBy(() -> service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_BAD_GATEWAY));

        assertThat(gateway.ownedStopCommands).isEmpty();
        assertThat(gateway.stopCommands).isEmpty();
    }

    @Test
    void stopTrackedProcessFailsClosedWhenUserIdentityIsMissing() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.healthy(11111L, "ok"));
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        OpencodeProcessStopService service = new OpencodeProcessStopService(
                gateway,
                repository,
                null,
                userRepository,
                null,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        assertThat(gateway.ownedStopCommands).isEmpty();
        assertThat(gateway.stopCommands).isEmpty();
    }

    @Test
    void stopTrackedProcessNeverFallsBackWhenManagerDoesNotSupportOwnedStop() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess running = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(running.processId(), running);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.healthy(11111L, "ok"));
        gateway.ownedStopFailure = new PlatformException(
                ErrorCode.OPENCODE_BAD_GATEWAY,
                "manager does not support stopOwned");
        OpencodeProcessStopService service = service(repository, gateway);

        assertThatThrownBy(() -> service.stopAndVerify(OpencodeProcessStopRequest.tracked(running, TRACE_ID)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_BAD_GATEWAY));

        assertThat(gateway.ownedStopCommands).hasSize(1);
        assertThat(gateway.stopCommands).isEmpty();
        assertThat(gateway.healthCommands).hasSize(1);
    }

    @Test
    void stopStartedInstanceUsesExactOwnershipAndReadOnlyPostProbe() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess started = process("ocp_started", 4097, OpencodeServerProcessStatus.RUNNING);
        repository.processes.put(started.processId(), started);
        repository.saveCount = 0;
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.notRunning("process not found"));
        OpencodeProcessStopService service = service(repository, gateway);

        OpencodeProcessControlResult result = service.stopStartedInstanceAndVerify(
                started,
                UNIFIED_AUTH_ID,
                33333L,
                TRACE_ID);

        assertThat(result.status()).isEqualTo("STOPPED");
        assertThat(gateway.ownedStopCommands).singleElement().satisfies(command -> {
            assertThat(command.expectedUnifiedAuthId()).isEqualTo(UNIFIED_AUTH_ID);
            assertThat(command.expectedPid()).isEqualTo(33333L);
        });
        assertThat(gateway.stopCommands).isEmpty();
        assertThat(gateway.healthCommands).hasSize(1);
        assertThat(repository.saveCount).isZero();
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
        return new OpencodeProcessStopService(
                gateway,
                repository,
                null,
                userRepository(),
                null,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UserRepository userRepository() {
        UserRepository repository = Mockito.mock(UserRepository.class);
        Mockito.when(repository.findByUserId(Mockito.any(UserId.class)))
                .thenReturn(Optional.of(User.createNew(
                        "usr_1234567890abcdef",
                        UNIFIED_AUTH_ID,
                        "test-user",
                        "password-hash",
                        null,
                        null,
                        null)));
        return repository;
    }

    private static OpencodeServerProcess process(String processId, int port, OpencodeServerProcessStatus status) {
        return process(processId, port, 11111L, status);
    }

    private static OpencodeServerProcess process(
            String processId,
            int port,
            Long pid,
            OpencodeServerProcessStatus status) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId),
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                port,
                pid,
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
        private final List<OpencodeProcessOwnedStopCommand> ownedStopCommands = new ArrayList<>();
        private final List<OpencodeProcessHealthCommand> healthCommands = new ArrayList<>();
        private final java.util.ArrayDeque<OpencodeProcessHealthResult> healthResults = new java.util.ArrayDeque<>();
        private PlatformException healthFailure;
        private PlatformException ownedStopFailure;

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            healthCommands.add(command);
            if (healthFailure != null) {
                throw healthFailure;
            }
            return healthResults.isEmpty()
                    ? OpencodeProcessHealthResult.notRunning("process not found")
                    : healthResults.removeFirst();
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
            return stoppedResult(command.port(), command.traceId());
        }

        @Override
        public OpencodeProcessControlResult stopOwnedProcess(OpencodeProcessOwnedStopCommand command) {
            ownedStopCommands.add(command);
            if (ownedStopFailure != null) {
                throw ownedStopFailure;
            }
            return stoppedResult(command.port(), command.traceId());
        }

        private OpencodeProcessControlResult stoppedResult(int port, String traceId) {
            return new OpencodeProcessControlResult(
                    "stop",
                    "STOPPED",
                    port,
                    11111L,
                    "http://10.8.0.12:" + port,
                    "/data/opencode/session/" + port,
                    "/data/opencode/.config/opencode/",
                    true,
                    "opencode server stopped",
                    traceId);
        }
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository {
        private final Map<OpencodeProcessId, OpencodeServerProcess> processes = new LinkedHashMap<>();
        private int saveCount;

        @Override public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) { saveCount++; processes.put(process.processId(), process); return process; }
        @Override public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) { return Optional.ofNullable(processes.get(processId)); }
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
        @Override public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) { return List.of(); }
        @Override public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) { return binding; }
        @Override public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) { return Optional.empty(); }
        @Override public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return List.copyOf(processes.values()); }
        @Override public PageResponse<OpencodeServerProcess> findOpencodeServerProcesses(OpencodeServerProcessFilter filter, PageRequest pageRequest) { return new PageResponse<>(List.copyOf(processes.values()), 1, processes.size(), processes.size()); }
    }
}
