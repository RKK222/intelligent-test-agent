package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
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
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessStartOperationRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerCommandNotDispatchedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OpencodeProcessStartupServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("10.8.0.12");
    private static final OpencodeContainerId CONTAINER_ID = new OpencodeContainerId("ctr_01");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void startAndVerifySavesCandidateChecksHealthAndMarksRunning() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStartupService service = service(repository, gateway, heartbeatStore);

        OpencodeServerProcess process = service.startAndVerify(request(null, null, null));

        assertThat(gateway.startCommands).hasSize(1);
        assertThat(gateway.healthCommands).singleElement().satisfies(command -> {
            assertThat(command.processId()).isEqualTo(process.processId());
            assertThat(command.baseUrl()).isEqualTo("http://10.8.0.12:4097");
        });
        assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
        assertThat(process.pid()).isEqualTo(12345L);
        assertThat(process.healthMessage()).isEqualTo("ok");
        assertThat(repository.findUserBinding(USER_ID, "opencode")).get()
                .extracting(UserOpencodeProcessBinding::processId)
                .isEqualTo(process.processId());
        assertThat(repository.savedNodes).singleElement()
                .extracting(ExecutionNode::baseUrl)
                .isEqualTo("http://10.8.0.12:4097");
        assertThat(heartbeatStore.liveOpencodeProcessIds()).contains(process.processId());
    }

    @Test
    void startAndVerifyReusesExistingProcessAndBindingTimestamps() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStartupService service = service(repository, gateway, heartbeatStore);
        OpencodeProcessId processId = new OpencodeProcessId("ocp_existing");
        Instant createdAt = NOW.minusSeconds(3600);
        Instant bindingCreatedAt = NOW.minusSeconds(1800);

        OpencodeServerProcess process = service.startAndVerify(request(processId, createdAt, bindingCreatedAt));

        assertThat(process.processId()).isEqualTo(processId);
        assertThat(process.createdAt()).isEqualTo(createdAt);
        assertThat(repository.findUserBinding(USER_ID, "opencode")).get().satisfies(binding -> {
            assertThat(binding.processId()).isEqualTo(processId);
            assertThat(binding.createdAt()).isEqualTo(bindingCreatedAt);
        });
    }

    @Test
    void startAndVerifyInjectsReferencesDirectoryResolvedForTargetPlatform() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        CommonParameterValues commonParameterValues = Mockito.mock(CommonParameterValues.class);
        Mockito.when(commonParameterValues.resolvedValue("OPENCODE_REFERENCES_DIR", ParameterPlatform.current()))
                .thenReturn(Optional.of(" /data/testagent/references "));
        OpencodeProcessStartupService service = new OpencodeProcessStartupService(
                repository,
                repository,
                gateway,
                new RecordingHeartbeatStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                commonParameterValues);

        service.startAndVerify(request(null, null, null));

        assertThat(gateway.startCommands).singleElement().satisfies(command ->
                assertThat(command.environment())
                        .containsEntry("OPENCODE_REFERENCES_DIR", "/data/testagent/references"));
        Mockito.verify(commonParameterValues)
                .resolvedValue("OPENCODE_REFERENCES_DIR", ParameterPlatform.current());
    }

    @Test
    void startAndVerifySkipsReferencesDirectoryWhenTargetPlatformParameterIsMissing() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        CommonParameterValues commonParameterValues = Mockito.mock(CommonParameterValues.class);
        Mockito.when(commonParameterValues.resolvedValue("OPENCODE_REFERENCES_DIR", ParameterPlatform.current()))
                .thenReturn(Optional.empty());
        OpencodeProcessStartupService service = new OpencodeProcessStartupService(
                repository,
                repository,
                gateway,
                new RecordingHeartbeatStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                commonParameterValues);

        service.startAndVerify(request(null, null, null));

        assertThat(gateway.startCommands).singleElement().satisfies(command ->
                assertThat(command.environment()).doesNotContainKey("OPENCODE_REFERENCES_DIR"));
    }

    @Test
    void startAndVerifyKeepsExplicitReferencesDirectoryFromCaller() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        CommonParameterValues commonParameterValues = Mockito.mock(CommonParameterValues.class);
        Mockito.when(commonParameterValues.resolvedValue("OPENCODE_REFERENCES_DIR", ParameterPlatform.current()))
                .thenReturn(Optional.of("/data/platform/references"));
        OpencodeProcessStartupService service = new OpencodeProcessStartupService(
                repository,
                repository,
                gateway,
                new RecordingHeartbeatStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                commonParameterValues);

        service.startAndVerify(request(
                null,
                null,
                null,
                Map.of("OPENCODE_REFERENCES_DIR", "/data/caller/references")));

        assertThat(gateway.startCommands).singleElement().satisfies(command ->
                assertThat(command.environment())
                        .containsEntry("OPENCODE_REFERENCES_DIR", "/data/caller/references"));
    }

    @Test
    void startAndVerifyInjectsTrustedFourLayerConfigRoots() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        CommonParameterValues commonParameterValues = Mockito.mock(CommonParameterValues.class);
        Mockito.when(commonParameterValues.resolvedValue("OPENCODE_PUBLIC_CONFIG_WORKTREE_ROOT", ParameterPlatform.current()))
                .thenReturn(Optional.of("/data/configdev"));
        Mockito.when(commonParameterValues.resolvedValue("OPENCODE_APP_WORKSPACE_ROOT", ParameterPlatform.current()))
                .thenReturn(Optional.of("/data/appworkspace"));
        Mockito.when(commonParameterValues.resolvedValue("OPENCODE_PERSONAL_WORKTREE_ROOT", ParameterPlatform.current()))
                .thenReturn(Optional.of("/data/personalworktree"));
        OpencodeProcessStartupService service = new OpencodeProcessStartupService(
                repository,
                repository,
                gateway,
                new RecordingHeartbeatStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                commonParameterValues);

        service.startAndVerify(request(null, null, null));

        assertThat(gateway.startCommands).singleElement().satisfies(command ->
                assertThat(command.environment())
                        .containsEntry(
                                "OPENCODE_PUBLIC_PERSONAL_CONFIG_DIR",
                                "/data/configdev/public-usr_1234567890abcdef/opencode")
                        .containsEntry("OPENCODE_APP_WORKSPACE_ROOT", "/data/appworkspace")
                        .containsEntry("OPENCODE_PERSONAL_WORKTREE_ROOT", "/data/personalworktree"));
    }

    @Test
    void successfulRestartInvalidatesPreviousProcessConversationContexts() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        ConversationContextStore contextStore = Mockito.mock(ConversationContextStore.class);
        OpencodeProcessStartupService service = new OpencodeProcessStartupService(
                repository,
                repository,
                gateway,
                heartbeatStore,
                contextStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
        OpencodeProcessId processId = new OpencodeProcessId("ocp_existing");

        service.startAndVerify(request(processId, NOW.minusSeconds(3600), NOW.minusSeconds(1800)));

        Mockito.verify(contextStore).invalidateProcess(processId.value());
    }

    @Test
    void startAndVerifyDoesNotReturnSuccessWhenHealthIsUnhealthy() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("opencode http health failed");
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStartupService service = service(repository, gateway, heartbeatStore);

        assertThatThrownBy(() -> service.startAndVerify(request(new OpencodeProcessId("ocp_failed"), NOW, NOW)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        OpencodeServerProcess process = repository.findOpencodeServerProcessById(new OpencodeProcessId("ocp_failed"))
                .orElseThrow();
        assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.UNHEALTHY);
        assertThat(process.healthMessage()).isEqualTo("opencode http health failed");
        assertThat(heartbeatStore.liveOpencodeProcessIds()).isEmpty();
    }

    @Test
    void startAndVerifyWaitsForTransientUnhealthyHealthBeforeMarkingRunning() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthResults.add(OpencodeProcessHealthResult.unhealthy("opencode health endpoints are not reachable"));
        gateway.healthResults.add(OpencodeProcessHealthResult.healthy("ok"));
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStartupService service = service(repository, gateway, heartbeatStore);

        OpencodeServerProcess process = service.startAndVerify(request(new OpencodeProcessId("ocp_transient"), NOW, NOW));

        assertThat(gateway.healthCommands).hasSize(2);
        assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
        assertThat(process.healthMessage()).isEqualTo("ok");
        assertThat(heartbeatStore.liveOpencodeProcessIds()).contains(process.processId());
        assertThat(repository.findUserBinding(USER_ID, "opencode")).isPresent();
    }

    @Test
    void startAndVerifyTimesOutWhenHealthNeverBecomesHealthy() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("opencode health endpoints are not reachable");
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStartupService service = service(repository, gateway, heartbeatStore);

        assertThatThrownBy(() -> service.startAndVerify(request(new OpencodeProcessId("ocp_timeout"), NOW, NOW)))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
                    assertThat(exception.getMessage()).contains("启动后 10 秒内未通过健康检查");
                });

        assertThat(gateway.healthCommands).hasSizeGreaterThan(1);
        OpencodeServerProcess process = repository.findOpencodeServerProcessById(new OpencodeProcessId("ocp_timeout"))
                .orElseThrow();
        assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.UNHEALTHY);
        assertThat(process.healthMessage()).isEqualTo("opencode health endpoints are not reachable");
        assertThat(heartbeatStore.liveOpencodeProcessIds()).isEmpty();
        assertThat(repository.findUserBinding(USER_ID, "opencode")).isEmpty();
    }

    @Test
    void startAndVerifyDoesNotRetryManagerControlFailure() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthFailure = new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "manager command timeout");
        OpencodeProcessStartupService service = service(repository, gateway, new RecordingHeartbeatStore());

        assertThatThrownBy(() -> service.startAndVerify(request(new OpencodeProcessId("ocp_manager_timeout"), NOW, NOW)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_TIMEOUT));

        assertThat(gateway.healthCommands).hasSize(1);
        assertThat(repository.findOpencodeServerProcessById(new OpencodeProcessId("ocp_manager_timeout")))
                .get()
                .extracting(OpencodeServerProcess::status)
                .isEqualTo(OpencodeServerProcessStatus.FAILED);
    }

    @Test
    void startAndVerifyLeavesOperationOpenWhenManagerCommandWasNotDispatched() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        gateway.startFailure = new ManagerCommandNotDispatchedException(CONTAINER_ID);
        OpencodeProcessStartOperationRepository operationRepository = Mockito.mock(OpencodeProcessStartOperationRepository.class);
        OpencodeProcessStartProgress progress = OpencodeProcessStartProgress.start(
                operationRepository,
                "opi_1234567890abcdef",
                USER_ID,
                "opencode",
                TRACE_ID,
                () -> NOW);
        OpencodeProcessStartupService service = service(repository, gateway, new RecordingHeartbeatStore());

        assertThatThrownBy(() -> service.startAndVerify(request(null, null, null), progress))
                .isInstanceOf(ManagerCommandNotDispatchedException.class);

        Mockito.verify(operationRepository, Mockito.never()).markFailed(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any());
    }

    @Test
    void startAndVerifyMapsNotRunningHealthToStopped() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("process pid is not alive");
        OpencodeProcessStartupService service = service(repository, gateway, new RecordingHeartbeatStore());

        assertThatThrownBy(() -> service.startAndVerify(request(new OpencodeProcessId("ocp_stopped"), NOW, NOW)))
                .isInstanceOf(PlatformException.class);

        assertThat(repository.findOpencodeServerProcessById(new OpencodeProcessId("ocp_stopped")))
                .get()
                .extracting(OpencodeServerProcess::status)
                .isEqualTo(OpencodeServerProcessStatus.STOPPED);
    }

    private static OpencodeProcessStartupService service(
            FakeRepository repository,
            RecordingGateway gateway,
            RecordingHeartbeatStore heartbeatStore) {
        return new OpencodeProcessStartupService(
                repository,
                repository,
                gateway,
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OpencodeProcessStartupRequest request(
            OpencodeProcessId processId,
            Instant createdAt,
            Instant bindingCreatedAt) {
        return request(processId, createdAt, bindingCreatedAt, Map.of());
    }

    private static OpencodeProcessStartupRequest request(
            OpencodeProcessId processId,
            Instant createdAt,
            Instant bindingCreatedAt,
            Map<String, String> environment) {
        return new OpencodeProcessStartupRequest(
                USER_ID,
                processId,
                createdAt,
                bindingCreatedAt,
                SERVER_ID,
                CONTAINER_ID,
                4097,
                "http://10.8.0.12:4097",
                "/data/opencode/session/4097",
                "/data/opencode/.config/opencode/",
                environment,
                TRACE_ID);
    }

    private static final class RecordingGateway implements OpencodeProcessManagerGateway {
        private final List<OpencodeProcessStartCommand> startCommands = new ArrayList<>();
        private final List<OpencodeProcessHealthCommand> healthCommands = new ArrayList<>();
        private final Deque<OpencodeProcessHealthResult> healthResults = new ArrayDeque<>();
        private OpencodeProcessHealthResult health = OpencodeProcessHealthResult.healthy("ok");
        private RuntimeException healthFailure;
        private RuntimeException startFailure;

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            healthCommands.add(command);
            if (healthFailure != null) {
                throw healthFailure;
            }
            if (!healthResults.isEmpty()) {
                return healthResults.removeFirst();
            }
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

        @Override
        public OpencodeProcessControlResult restartProcess(OpencodeProcessControlCommand command) {
            throw new UnsupportedOperationException("restartProcess is not used");
        }

        @Override
        public OpencodeProcessControlResult stopProcess(OpencodeProcessControlCommand command) {
            throw new UnsupportedOperationException("stopProcess is not used");
        }
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository, ExecutionNodeRepository {
        private final Map<OpencodeProcessId, OpencodeServerProcess> processes = new LinkedHashMap<>();
        private final Map<String, UserOpencodeProcessBinding> bindings = new LinkedHashMap<>();
        private final List<ExecutionNode> savedNodes = new ArrayList<>();

        @Override
        public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) {
            processes.put(process.processId(), process);
            return process;
        }

        @Override
        public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) {
            return Optional.ofNullable(processes.get(processId));
        }

        @Override
        public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) {
            bindings.put(binding.userId().value() + ":" + binding.agentId(), binding);
            return binding;
        }

        @Override
        public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) {
            return Optional.ofNullable(bindings.get(userId.value() + ":" + agentId));
        }

        @Override
        public ExecutionNode save(ExecutionNode executionNode) {
            savedNodes.add(executionNode);
            return executionNode;
        }

        @Override public Optional<ExecutionNode> findById(ExecutionNodeId executionNodeId) { return Optional.empty(); }
        @Override public List<ExecutionNode> findRoutableNodes(int limit) { return savedNodes.stream().limit(limit).toList(); }
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
        @Override public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return List.of(); }
        @Override public PageResponse<OpencodeServerProcess> findOpencodeServerProcesses(OpencodeServerProcessFilter filter, PageRequest pageRequest) {
            return new PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
        }
    }

    private static final class RecordingHeartbeatStore implements OpencodeProcessHeartbeatStore {
        private final Set<OpencodeProcessId> liveOpencodeProcessIds = new LinkedHashSet<>();

        @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) { }
        @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) { }
        @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) { }
        @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) {
            liveOpencodeProcessIds.add(processId);
        }
        @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
        @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
        @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
        @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.copyOf(liveOpencodeProcessIds); }
        @Override public void cleanupExpiredHeartbeats() { }
    }
}
