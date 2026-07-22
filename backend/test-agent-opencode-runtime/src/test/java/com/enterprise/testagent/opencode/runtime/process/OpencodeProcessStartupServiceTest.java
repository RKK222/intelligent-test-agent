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
        assertThat(gateway.startCommands).singleElement().satisfies(command ->
                assertThat(command.unifiedAuthId()).isEqualTo("ucid_001"));
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
    void startAndVerifyPointsManagedConfigLinkToSharedBeforeManagerStart() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        OpencodeProcessStartupService service = service(repository, gateway, new RecordingHeartbeatStore());
        OpencodeProcessConfigLinkService configLinkService = Mockito.mock(OpencodeProcessConfigLinkService.class);
        service.setConfigLinkService(configLinkService);
        OpencodeProcessStartupRequest request = request(null, null, null);

        service.startAndVerify(request);

        Mockito.verify(configLinkService).switchToShared(request.sessionPath(), request.configPath());
        assertThat(gateway.startCommands).singleElement().satisfies(command ->
                assertThat(command.configPath()).isEqualTo(request.configPath()));
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
    void existingReservedStartupVerifiesBindingWithoutUnconditionalBindingSave() {
        FakeRepository repository = new FakeRepository();
        OpencodeProcessId processId = new OpencodeProcessId("ocp_reserved");
        OpencodeServerProcess reserved = reservedProcess(processId, CONTAINER_ID, 4097);
        UserOpencodeProcessBinding binding = binding(processId, 4097);
        repository.processes.put(processId, reserved);
        repository.bindings.put(USER_ID.value() + ":opencode", binding);
        RecordingGateway gateway = new RecordingGateway();
        OpencodeProcessStartupService service = service(repository, gateway, new RecordingHeartbeatStore());

        OpencodeServerProcess running = service.startAndVerify(request(processId, reserved.createdAt(), binding.createdAt()));

        assertThat(running.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
        assertThat(repository.savedBindingCount).isZero();
        assertThat(repository.findUserBinding(USER_ID, "opencode")).contains(binding);
    }

    @Test
    void staleStartupSnapshotCannotOverwriteConcurrentMigrationCoordinates() {
        FakeRepository repository = new FakeRepository();
        OpencodeProcessId processId = new OpencodeProcessId("ocp_racing_start");
        OpencodeServerProcess old = reservedProcess(processId, CONTAINER_ID, 4097);
        UserOpencodeProcessBinding oldBinding = binding(processId, 4097);
        repository.processes.put(processId, old);
        repository.bindings.put(USER_ID.value() + ":opencode", oldBinding);
        RecordingGateway gateway = new RecordingGateway();
        gateway.beforeStart = () -> {
            OpencodeContainerId replacementContainer = new OpencodeContainerId("ctr_new");
            repository.processes.put(processId, reservedProcess(processId, replacementContainer, 4200));
            repository.bindings.put(USER_ID.value() + ":opencode", new UserOpencodeProcessBinding(
                    USER_ID,
                    "opencode",
                    processId,
                    SERVER_ID,
                    4200,
                    UserOpencodeProcessBindingStatus.ACTIVE,
                    oldBinding.createdAt(),
                    NOW.plusSeconds(1),
                    TRACE_ID));
        };
        gateway.startResult = new OpencodeProcessStartResult(12345L, "started", true);
        OpencodeProcessStartupService service = service(repository, gateway, new RecordingHeartbeatStore());

        assertThatThrownBy(() -> service.startAndVerify(request(processId, old.createdAt(), oldBinding.createdAt())))
                .isInstanceOf(PlatformException.class);

        assertThat(repository.processes.get(processId)).satisfies(actual -> {
            assertThat(actual.containerId()).isEqualTo(new OpencodeContainerId("ctr_new"));
            assertThat(actual.port()).isEqualTo(4200);
        });
        assertThat(repository.bindings.get(USER_ID.value() + ":opencode").port()).isEqualTo(4200);
        assertThat(gateway.ownedStopCommands).singleElement().satisfies(command -> {
            assertThat(command.containerId()).isEqualTo(CONTAINER_ID);
            assertThat(command.port()).isEqualTo(4097);
            assertThat(command.expectedUnifiedAuthId()).isEqualTo("ucid_001");
            assertThat(command.expectedPid()).isEqualTo(12345L);
        });
        assertThat(gateway.stopCommands).isEmpty();
        assertThat(gateway.healthCommands).singleElement().satisfies(command -> {
            assertThat(command.processId()).isEqualTo(processId);
            assertThat(command.containerId()).isEqualTo(CONTAINER_ID);
            assertThat(command.baseUrl()).isEqualTo("http://10.8.0.12:4097");
        });
    }

    @Test
    void staleReusedStartupSnapshotDoesNotStopConcurrentWinner() {
        FakeRepository repository = new FakeRepository();
        OpencodeProcessId processId = new OpencodeProcessId("ocp_reused_racing_start");
        OpencodeServerProcess old = reservedProcess(processId, CONTAINER_ID, 4097);
        UserOpencodeProcessBinding oldBinding = binding(processId, 4097);
        repository.processes.put(processId, old);
        repository.bindings.put(USER_ID.value() + ":opencode", oldBinding);
        RecordingGateway gateway = new RecordingGateway();
        gateway.startResult = new OpencodeProcessStartResult(12345L, "reused", false);
        gateway.beforeStart = () -> repository.processes.put(
                processId,
                reservedProcess(processId, new OpencodeContainerId("ctr_new"), 4200));
        OpencodeProcessStartupService service = service(repository, gateway, new RecordingHeartbeatStore());

        assertThatThrownBy(() -> service.startAndVerify(request(processId, old.createdAt(), oldBinding.createdAt())))
                .isInstanceOf(PlatformException.class);

        assertThat(gateway.ownedStopCommands).isEmpty();
        assertThat(gateway.stopCommands).isEmpty();
        assertThat(gateway.healthCommands).isEmpty();
    }

    @Test
    void staleLegacyStartupSnapshotWithUnknownCreationDoesNotRiskStoppingReusedInstance() {
        FakeRepository repository = new FakeRepository();
        OpencodeProcessId processId = new OpencodeProcessId("ocp_legacy_racing_start");
        OpencodeServerProcess old = reservedProcess(processId, CONTAINER_ID, 4097);
        UserOpencodeProcessBinding oldBinding = binding(processId, 4097);
        repository.processes.put(processId, old);
        repository.bindings.put(USER_ID.value() + ":opencode", oldBinding);
        RecordingGateway gateway = new RecordingGateway();
        gateway.startResult = new OpencodeProcessStartResult(12345L, "legacy response");
        gateway.beforeStart = () -> repository.processes.put(
                processId,
                reservedProcess(processId, new OpencodeContainerId("ctr_new"), 4200));
        OpencodeProcessStartupService service = service(repository, gateway, new RecordingHeartbeatStore());

        assertThatThrownBy(() -> service.startAndVerify(request(processId, old.createdAt(), oldBinding.createdAt())))
                .isInstanceOf(PlatformException.class);

        assertThat(gateway.ownedStopCommands).isEmpty();
        assertThat(gateway.stopCommands).isEmpty();
        assertThat(gateway.healthCommands).isEmpty();
    }

    @Test
    void freshStartupFinalStateCasConflictCompensatesWithoutOverwritingMigration() {
        FakeRepository repository = new FakeRepository();
        OpencodeProcessId processId = new OpencodeProcessId("ocp_final_state_race");
        OpencodeServerProcess old = reservedProcess(processId, CONTAINER_ID, 4097);
        UserOpencodeProcessBinding oldBinding = binding(processId, 4097);
        repository.processes.put(processId, old);
        repository.bindings.put(USER_ID.value() + ":opencode", oldBinding);
        RecordingGateway gateway = new RecordingGateway();
        gateway.startResult = new OpencodeProcessStartResult(12345L, "started", true);
        java.util.concurrent.atomic.AtomicBoolean migrated = new java.util.concurrent.atomic.AtomicBoolean();
        gateway.beforeHealth = () -> {
            if (migrated.compareAndSet(false, true)) {
                OpencodeContainerId replacementContainer = new OpencodeContainerId("ctr_new");
                repository.processes.put(processId, reservedProcess(processId, replacementContainer, 4200));
                repository.bindings.put(USER_ID.value() + ":opencode", new UserOpencodeProcessBinding(
                        USER_ID,
                        "opencode",
                        processId,
                        SERVER_ID,
                        4200,
                        UserOpencodeProcessBindingStatus.ACTIVE,
                        oldBinding.createdAt(),
                        NOW.plusSeconds(1),
                        TRACE_ID));
            }
        };
        OpencodeProcessStartupService service = service(repository, gateway, new RecordingHeartbeatStore());

        assertThatThrownBy(() -> service.startAndVerify(request(processId, old.createdAt(), oldBinding.createdAt())))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE));

        assertThat(repository.processes.get(processId)).satisfies(actual -> {
            assertThat(actual.containerId()).isEqualTo(new OpencodeContainerId("ctr_new"));
            assertThat(actual.port()).isEqualTo(4200);
        });
        assertThat(gateway.ownedStopCommands).singleElement().satisfies(command -> {
            assertThat(command.containerId()).isEqualTo(CONTAINER_ID);
            assertThat(command.port()).isEqualTo(4097);
            assertThat(command.expectedUnifiedAuthId()).isEqualTo("ucid_001");
            assertThat(command.expectedPid()).isEqualTo(12345L);
        });
        assertThat(gateway.stopCommands).isEmpty();
        assertThat(gateway.healthCommands).hasSize(2);
    }

    @Test
    void sameAssignmentNewLifecycleCannotBeOverwrittenAndCompensationFailsClosed() {
        FakeRepository repository = new FakeRepository();
        OpencodeProcessId processId = new OpencodeProcessId("ocp_same_assignment_new_lifecycle");
        OpencodeServerProcess old = reservedProcess(processId, CONTAINER_ID, 4097);
        UserOpencodeProcessBinding oldBinding = binding(processId, 4097);
        repository.processes.put(processId, old);
        repository.bindings.put(USER_ID.value() + ":opencode", oldBinding);
        RecordingGateway gateway = new RecordingGateway();
        gateway.startResult = new OpencodeProcessStartResult(12345L, "old lifecycle started", true);
        gateway.ownedStopFailure = new PlatformException(
                ErrorCode.OPENCODE_BAD_GATEWAY,
                "owned process mismatch");
        java.util.concurrent.atomic.AtomicBoolean replaced = new java.util.concurrent.atomic.AtomicBoolean();
        gateway.beforeHealth = () -> {
            if (replaced.compareAndSet(false, true)) {
                OpencodeServerProcess candidate = repository.processes.get(processId);
                repository.processes.put(processId, new OpencodeServerProcess(
                        candidate.processId(),
                        candidate.userId(),
                        candidate.linuxServerId(),
                        candidate.containerId(),
                        candidate.port(),
                        200L,
                        candidate.baseUrl(),
                        OpencodeServerProcessStatus.RUNNING,
                        candidate.sessionPath(),
                        candidate.configPath(),
                        candidate.startedAt(),
                        NOW.plusSeconds(1),
                        "new lifecycle running",
                        candidate.createdAt(),
                        NOW.plusSeconds(1),
                        "trace_new_lifecycle"));
            }
        };
        OpencodeProcessStartupService service = service(repository, gateway, new RecordingHeartbeatStore());

        assertThatThrownBy(() -> service.startAndVerify(request(processId, old.createdAt(), oldBinding.createdAt())))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
                    assertThat(exception.getSuppressed()).singleElement().isInstanceOf(PlatformException.class);
                });

        assertThat(repository.processes.get(processId)).satisfies(actual -> {
            assertThat(actual.containerId()).isEqualTo(CONTAINER_ID);
            assertThat(actual.port()).isEqualTo(4097);
            assertThat(actual.pid()).isEqualTo(200L);
            assertThat(actual.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
            assertThat(actual.traceId()).isEqualTo("trace_new_lifecycle");
        });
        assertThat(gateway.ownedStopCommands).singleElement().satisfies(command -> {
            assertThat(command.expectedUnifiedAuthId()).isEqualTo("ucid_001");
            assertThat(command.expectedPid()).isEqualTo(12345L);
        });
        assertThat(gateway.stopCommands).isEmpty();
    }

    @Test
    void freshConflictOwnershipMismatchNeverFallsBackOrOverwritesConcurrentWinner() {
        FakeRepository repository = new FakeRepository();
        OpencodeProcessId processId = new OpencodeProcessId("ocp_cleanup_ownership_race");
        OpencodeServerProcess old = reservedProcess(processId, CONTAINER_ID, 4097);
        UserOpencodeProcessBinding oldBinding = binding(processId, 4097);
        repository.processes.put(processId, old);
        repository.bindings.put(USER_ID.value() + ":opencode", oldBinding);
        RecordingGateway gateway = new RecordingGateway();
        gateway.startResult = new OpencodeProcessStartResult(12345L, "started", true);
        gateway.ownedStopFailure = new PlatformException(
                ErrorCode.OPENCODE_BAD_GATEWAY,
                "owned process mismatch");
        gateway.beforeStart = () -> {
            repository.processes.put(
                    processId,
                    reservedProcess(processId, new OpencodeContainerId("ctr_new"), 4200));
            repository.bindings.put(USER_ID.value() + ":opencode", new UserOpencodeProcessBinding(
                    USER_ID,
                    "opencode",
                    processId,
                    SERVER_ID,
                    4200,
                    UserOpencodeProcessBindingStatus.ACTIVE,
                    oldBinding.createdAt(),
                    NOW.plusSeconds(1),
                    TRACE_ID));
        };
        OpencodeProcessStartupService service = service(repository, gateway, new RecordingHeartbeatStore());

        assertThatThrownBy(() -> service.startAndVerify(request(processId, old.createdAt(), oldBinding.createdAt())))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
                    assertThat(exception.getSuppressed()).singleElement().isInstanceOf(PlatformException.class);
                });

        assertThat(repository.processes.get(processId)).satisfies(actual -> {
            assertThat(actual.containerId()).isEqualTo(new OpencodeContainerId("ctr_new"));
            assertThat(actual.port()).isEqualTo(4200);
        });
        assertThat(gateway.ownedStopCommands).hasSize(1);
        assertThat(gateway.stopCommands).isEmpty();
        assertThat(gateway.healthCommands).isEmpty();
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
        gateway.health = OpencodeProcessHealthResult.managedUnhealthy(12345L, "opencode http health failed");
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
        gateway.healthResults.add(OpencodeProcessHealthResult.managedUnhealthy(
                12345L, "opencode health endpoints are not reachable"));
        gateway.healthResults.add(OpencodeProcessHealthResult.healthy(12345L, "ok"));
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
        gateway.health = OpencodeProcessHealthResult.managedUnhealthy(
                12345L, "opencode health endpoints are not reachable");
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
                "/data/opencode/session/users/ucid_001",
                "/data/opencode/.config/opencode/",
                environment,
                TRACE_ID);
    }

    private static OpencodeServerProcess reservedProcess(
            OpencodeProcessId processId,
            OpencodeContainerId containerId,
            int port) {
        return new OpencodeServerProcess(
                processId,
                USER_ID,
                SERVER_ID,
                containerId,
                port,
                null,
                "http://10.8.0.12:" + port,
                OpencodeServerProcessStatus.STARTING,
                "/data/opencode/session/users/ucid_001",
                "/data/opencode/.config/opencode/",
                NOW,
                NOW,
                "reserved",
                NOW.minusSeconds(3600),
                NOW,
                TRACE_ID);
    }

    private static UserOpencodeProcessBinding binding(OpencodeProcessId processId, int port) {
        return new UserOpencodeProcessBinding(
                USER_ID,
                "opencode",
                processId,
                SERVER_ID,
                port,
                UserOpencodeProcessBindingStatus.ACTIVE,
                NOW.minusSeconds(1800),
                NOW,
                TRACE_ID);
    }

    private static final class RecordingGateway implements OpencodeProcessManagerGateway {
        private final List<OpencodeProcessStartCommand> startCommands = new ArrayList<>();
        private final List<OpencodeProcessHealthCommand> healthCommands = new ArrayList<>();
        private final List<OpencodeProcessControlCommand> stopCommands = new ArrayList<>();
        private final List<OpencodeProcessOwnedStopCommand> ownedStopCommands = new ArrayList<>();
        private final Deque<OpencodeProcessHealthResult> healthResults = new ArrayDeque<>();
        private OpencodeProcessHealthResult health = OpencodeProcessHealthResult.healthy(12345L, "ok");
        private OpencodeProcessStartResult startResult = new OpencodeProcessStartResult(12345L, "started");
        private RuntimeException healthFailure;
        private RuntimeException startFailure;
        private RuntimeException ownedStopFailure;
        private Runnable beforeStart = () -> { };
        private Runnable beforeHealth = () -> { };

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            healthCommands.add(command);
            beforeHealth.run();
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
            beforeStart.run();
            if (startFailure != null) {
                throw startFailure;
            }
            startCommands.add(command);
            return startResult;
        }

        @Override
        public OpencodeProcessControlResult restartProcess(OpencodeProcessControlCommand command) {
            throw new UnsupportedOperationException("restartProcess is not used");
        }

        @Override
        public OpencodeProcessControlResult stopProcess(OpencodeProcessControlCommand command) {
            stopCommands.add(command);
            return stopped(command.port(), command.traceId());
        }

        @Override
        public OpencodeProcessControlResult stopOwnedProcess(OpencodeProcessOwnedStopCommand command) {
            ownedStopCommands.add(command);
            if (ownedStopFailure != null) {
                throw ownedStopFailure;
            }
            health = OpencodeProcessHealthResult.notRunning("process not found");
            return stopped(command.port(), command.traceId());
        }

        private OpencodeProcessControlResult stopped(int port, String traceId) {
            return new OpencodeProcessControlResult(
                    "stop",
                    "STOPPED",
                    port,
                    null,
                    "http://10.8.0.12:" + port,
                    "/data/opencode/session/users/ucid_001",
                    "/data/opencode/.config/opencode/",
                    false,
                    "stopped",
                    traceId);
        }
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository, ExecutionNodeRepository {
        private final Map<OpencodeProcessId, OpencodeServerProcess> processes = new LinkedHashMap<>();
        private final Map<String, UserOpencodeProcessBinding> bindings = new LinkedHashMap<>();
        private final List<ExecutionNode> savedNodes = new ArrayList<>();
        private int savedBindingCount;

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
            savedBindingCount++;
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
