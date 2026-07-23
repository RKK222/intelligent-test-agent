package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAtomicMutationPort;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.user.UserId;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class OpencodeProcessStatusQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void missingProcessReturnsNotStartedWithoutGatewayOrDatabaseWrite() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStatusQueryService service = service(repository, gateway, heartbeatStore);

        OpencodeProcessStatusProbe probe = service.query(new OpencodeProcessId("ocp_missing"), TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.NOT_STARTED);
        assertThat(probe.process()).isEmpty();
        assertThat(probe.managerStatus()).isEqualTo("NOT_RUNNING");
        assertThat(probe.healthStatus()).isEqualTo("NOT_RUNNING");
        assertThat(probe.restartable()).isTrue();
        assertThat(gateway.healthCommands).isEmpty();
        assertThat(repository.savedProcesses).isEmpty();
        assertThat(heartbeatStore.recordedProcessIds).isEmpty();
    }

    @Test
    void healthyProcessRefreshesRunningSnapshotAndHeartbeat() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess old = process("ocp_running", 4097, OpencodeServerProcessStatus.UNHEALTHY, 11111L);
        repository.processes.put(old.processId(), old);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.healthy(11111L, "ok");
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStatusQueryService service = service(repository, gateway, heartbeatStore);

        OpencodeProcessStatusProbe probe = service.query(old.processId(), TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.RUNNING);
        assertThat(probe.process()).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
            assertThat(process.pid()).isEqualTo(11111L);
            assertThat(process.healthMessage()).isEqualTo("ok");
            assertThat(process.lastHealthCheckAt()).isEqualTo(NOW);
            assertThat(process.updatedAt()).isEqualTo(NOW);
        });
        assertThat(probe.managerStatus()).isEqualTo("RUNNING");
        assertThat(probe.healthStatus()).isEqualTo("HEALTHY");
        assertThat(probe.restartable()).isFalse();
        assertThat(repository.savedProcesses).containsKey(old.processId());
        assertThat(heartbeatStore.recordedProcessIds).containsExactly(old.processId());
    }

    @Test
    void cachedRunningSnapshotHealthCheckDoesNotReadOrRewriteRepository() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess cached = process("ocp_running", 4097, OpencodeServerProcessStatus.RUNNING, 11111L);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.healthy(11111L, "ok");
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStatusQueryService service = service(repository, gateway, heartbeatStore);

        OpencodeProcessStatusProbe probe = service.querySnapshot(cached, TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.RUNNING);
        assertThat(probe.process()).contains(cached);
        assertThat(repository.findProcessCalls).isZero();
        assertThat(repository.savedProcesses).isEmpty();
        assertThat(heartbeatStore.recordedProcessIds).containsExactly(cached.processId());
    }

    @Test
    void readOnlySnapshotReturnsFreshRunningStateWithoutDatabaseOrHeartbeatPersistence() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess cached = process("ocp_read_only", 4097, OpencodeServerProcessStatus.UNHEALTHY, 11111L);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.healthy(33333L, "ok");
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStatusQueryService service = service(repository, gateway, heartbeatStore);

        OpencodeProcessStatusProbe probe = service.querySnapshotReadOnly(cached, TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.RUNNING);
        assertThat(probe.process()).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
            assertThat(process.pid()).isEqualTo(33333L);
            assertThat(process.healthMessage()).isEqualTo("ok");
        });
        assertThat(repository.findProcessCalls).isZero();
        assertThat(repository.savedProcesses).isEmpty();
        assertThat(heartbeatStore.recordedProcessIds).isEmpty();
    }

    @Test
    void readOnlyHealthyWithoutManagerPidFailsClosedInsteadOfReusingDatabasePid() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess cached = process(
                "ocp_read_only_missing_pid",
                4097,
                OpencodeServerProcessStatus.RUNNING,
                11111L);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.healthy("manager omitted pid");
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStatusQueryService service = service(repository, gateway, heartbeatStore);

        OpencodeProcessStatusProbe probe = service.querySnapshotReadOnly(cached, TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.STALE);
        assertThat(probe.errorCode()).isEqualTo(ErrorCode.OPENCODE_BAD_GATEWAY);
        assertThat(probe.process()).contains(cached);
        assertThat(repository.findProcessCalls).isZero();
        assertThat(repository.savedProcesses).isEmpty();
        assertThat(heartbeatStore.recordedProcessIds).isEmpty();
    }

    @Test
    void readOnlySnapshotConfirmsNotManagedWithoutStoppedStatePersistence() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess cached = process("ocp_read_only_stopped", 4097, OpencodeServerProcessStatus.RUNNING, 11111L);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.notRunning("not managed");
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        OpencodeProcessStatusQueryService service = service(repository, gateway, heartbeatStore);

        OpencodeProcessStatusProbe probe = service.querySnapshotReadOnly(cached, TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.NOT_STARTED);
        assertThat(probe.process()).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.STOPPED);
            assertThat(process.pid()).isNull();
        });
        assertThat(repository.findProcessCalls).isZero();
        assertThat(repository.savedProcesses).isEmpty();
        assertThat(heartbeatStore.recordedProcessIds).isEmpty();
    }

    @Test
    void cachedSnapshotPersistsOnlyARealStatusTransitionWithoutRepositoryRead() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess cached = process("ocp_unhealthy", 4097, OpencodeServerProcessStatus.UNHEALTHY, 11111L);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.healthy(11111L, "ok");
        OpencodeProcessStatusQueryService service = new OpencodeProcessStatusQueryService(
                repository,
                gateway,
                new RecordingHeartbeatStore(),
                acceptingStateMutation(repository),
                Clock.fixed(NOW, ZoneOffset.UTC));

        OpencodeProcessStatusProbe probe = service.querySnapshot(cached, TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.RUNNING);
        assertThat(probe.process()).get().extracting(OpencodeServerProcess::status)
                .isEqualTo(OpencodeServerProcessStatus.RUNNING);
        assertThat(repository.findProcessCalls).isZero();
        assertThat(repository.savedProcesses).containsOnlyKeys(cached.processId());
    }

    private static OpencodeProcessAtomicMutationPort acceptingStateMutation(FakeRepository repository) {
        return new OpencodeProcessAtomicMutationPort() {
            @Override
            public void compareAndSetAssignment(
                    OpencodeServerProcess expectedProcess,
                    UserOpencodeProcessBinding expectedBinding,
                    OpencodeServerProcess replacementProcess,
                    UserOpencodeProcessBinding replacementBinding) {
                throw new UnsupportedOperationException("assignment migration is not used");
            }

            @Override
            public boolean compareAndSetRuntimeState(
                    OpencodeServerProcess expectedAssignment,
                    OpencodeServerProcess replacementState) {
                repository.saveOpencodeServerProcess(replacementState);
                return true;
            }
        };
    }

    @Test
    void healthyProcessRefreshesBaseUrlFromAdvertisedHostForStableServerId() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess old = process(
                "ocp_running",
                "linux-prod-a",
                "http://old-host:4097",
                4097,
                OpencodeServerProcessStatus.UNHEALTHY,
                11111L);
        repository.processes.put(old.processId(), old);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.healthy(11111L, "ok");
        OpencodeProcessStatusQueryService service = new OpencodeProcessStatusQueryService(
                repository,
                gateway,
                new RecordingHeartbeatStore(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpencodeServerAddressResolver("10.8.0.21"));

        OpencodeProcessStatusProbe probe = service.query(old.processId(), TRACE_ID);

        assertThat(probe.process()).get()
                .extracting(OpencodeServerProcess::baseUrl)
                .isEqualTo("http://10.8.0.21:4097");
    }

    @Test
    void notRunningHealthMessageMarksProcessStoppedAndClearsPid() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess old = process("ocp_stale", 4097, OpencodeServerProcessStatus.RUNNING, 22222L);
        repository.processes.put(old.processId(), old);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.unhealthy("port 4097 is not managed");
        OpencodeProcessStatusQueryService service = service(repository, gateway, new RecordingHeartbeatStore());

        OpencodeProcessStatusProbe probe = service.query(old.processId(), TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.NOT_STARTED);
        assertThat(probe.process()).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.STOPPED);
            assertThat(process.pid()).isNull();
            assertThat(process.healthMessage()).isEqualTo("port 4097 is not managed");
        });
        assertThat(probe.managerStatus()).isEqualTo("NOT_RUNNING");
        assertThat(probe.healthStatus()).isEqualTo("NOT_RUNNING");
        assertThat(probe.restartable()).isTrue();
    }

    @Test
    void managedPidWithUnhealthyHttpReturnsHealthCheckFailedWithoutDatabaseWrite() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess old = process("ocp_unhealthy", 4097, OpencodeServerProcessStatus.RUNNING, 22222L);
        repository.processes.put(old.processId(), old);
        RecordingGateway gateway = new RecordingGateway();
        gateway.health = OpencodeProcessHealthResult.managedUnhealthy(33333L, "opencode http health failed");
        OpencodeProcessStatusQueryService service = service(repository, gateway, new RecordingHeartbeatStore());

        OpencodeProcessStatusProbe probe = service.query(old.processId(), TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.HEALTH_CHECK_FAILED);
        assertThat(probe.errorCode()).isNull();
        assertThat(probe.process()).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
            assertThat(process.pid()).isEqualTo(33333L);
            assertThat(process.healthMessage()).isEqualTo("old");
        });
        assertThat(probe.managerStatus()).isEqualTo("RUNNING");
        assertThat(probe.healthStatus()).isEqualTo("UNHEALTHY");
        assertThat(probe.restartable()).isTrue();
        assertThat(repository.savedProcesses).isEmpty();
    }

    @Test
    void healthCommandExceptionReturnsStaleAndKeepsErrorCodeWithoutDatabaseWrite() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess old = process("ocp_failed", 4097, OpencodeServerProcessStatus.RUNNING, 22222L);
        repository.processes.put(old.processId(), old);
        RecordingGateway gateway = new RecordingGateway();
        gateway.healthFailure = new PlatformException(ErrorCode.OPENCODE_TIMEOUT, "manager command timeout");
        OpencodeProcessStatusQueryService service = service(repository, gateway, new RecordingHeartbeatStore());

        OpencodeProcessStatusProbe probe = service.query(old.processId(), TRACE_ID);

        assertThat(probe.status()).isEqualTo(OpencodeProcessProbeStatus.STALE);
        assertThat(probe.errorCode()).isEqualTo(ErrorCode.OPENCODE_TIMEOUT);
        assertThat(probe.process()).get().satisfies(process -> {
            assertThat(process.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
            assertThat(process.pid()).isEqualTo(22222L);
            assertThat(process.healthMessage()).isEqualTo("old");
        });
        assertThat(probe.managerStatus()).isEqualTo("STALE");
        assertThat(probe.healthStatus()).isEqualTo("STALE");
        assertThat(probe.restartable()).isFalse();
        assertThat(repository.savedProcesses).isEmpty();
    }

    @Test
    void weakHealthUsesRedisSnapshotAndDoesNotTouchRepositoryGatewayOrHeartbeat() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        heartbeatStore.managerSnapshots.add(managerSnapshot("server-a", "ctr_01", managedProcess(4096, "http://old-host:4096")));
        RecordingWeakHealthHttpClient healthClient = new RecordingWeakHealthHttpClient(true, "ok");
        OpencodeProcessStatusQueryService service = new OpencodeProcessStatusQueryService(
                repository,
                gateway,
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpencodeServerAddressResolver("10.8.0.21"),
                healthClient);

        OpencodeProcessWeakHealthResponse response = service.weakHealth(
                new OpencodeProcessWeakHealthRequest("server-a", "ctr_01", 4096),
                TRACE_ID);

        assertThat(response.healthy()).isTrue();
        assertThat(response.status()).isEqualTo(OpencodeProcessWeakHealthStatus.HEALTHY);
        assertThat(response.serviceStatus()).isEqualTo("RUNNING");
        assertThat(response.baseUrl()).isEqualTo("http://10.8.0.21:4096");
        assertThat(response.checkedAt()).isEqualTo(NOW);
        assertThat(healthClient.calls).containsExactly("http://10.8.0.21:4096|trace_1234567890abcdef");
        assertThat(repository.findProcessCalls).isZero();
        assertThat(repository.savedProcesses).isEmpty();
        assertThat(gateway.healthCommands).isEmpty();
        assertThat(heartbeatStore.recordedProcessIds).isEmpty();
    }

    @Test
    void weakHealthReturnsProcessNotFoundWhenManagerSnapshotDoesNotContainPort() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        heartbeatStore.managerSnapshots.add(managerSnapshot("server-a", "ctr_01", managedProcess(4097, "http://10.8.0.21:4097")));
        RecordingWeakHealthHttpClient healthClient = new RecordingWeakHealthHttpClient(true, "ok");
        OpencodeProcessStatusQueryService service = new OpencodeProcessStatusQueryService(
                repository,
                gateway,
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpencodeServerAddressResolver("10.8.0.21"),
                healthClient);

        OpencodeProcessWeakHealthResponse response = service.weakHealth(
                new OpencodeProcessWeakHealthRequest("server-a", "ctr_01", 4096),
                TRACE_ID);

        assertThat(response.healthy()).isFalse();
        assertThat(response.status()).isEqualTo(OpencodeProcessWeakHealthStatus.PROCESS_NOT_FOUND);
        assertThat(response.serviceStatus()).isEqualTo("NOT_RUNNING");
        assertThat(response.message()).contains("Redis");
        assertThat(healthClient.calls).isEmpty();
        assertThat(repository.findProcessCalls).isZero();
        assertThat(gateway.healthCommands).isEmpty();
    }

    @Test
    void weakHealthReturnsManagerUnavailableWhenRedisSnapshotIsMissing() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        RecordingWeakHealthHttpClient healthClient = new RecordingWeakHealthHttpClient(true, "ok");
        OpencodeProcessStatusQueryService service = new OpencodeProcessStatusQueryService(
                repository,
                gateway,
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpencodeServerAddressResolver("10.8.0.21"),
                healthClient);

        OpencodeProcessWeakHealthResponse response = service.weakHealth(
                new OpencodeProcessWeakHealthRequest("server-a", "ctr_01", 4096),
                TRACE_ID);

        assertThat(response.healthy()).isFalse();
        assertThat(response.status()).isEqualTo(OpencodeProcessWeakHealthStatus.MANAGER_UNAVAILABLE);
        assertThat(response.serviceStatus()).isEqualTo("NOT_RUNNING");
        assertThat(response.message()).contains("Redis");
        assertThat(healthClient.calls).isEmpty();
        assertThat(repository.findProcessCalls).isZero();
        assertThat(repository.savedProcesses).isEmpty();
        assertThat(gateway.healthCommands).isEmpty();
    }

    @Test
    void weakHealthMapsHttpFailureToUnhealthyWithoutDatabaseWrite() {
        FakeRepository repository = new FakeRepository();
        RecordingGateway gateway = new RecordingGateway();
        RecordingHeartbeatStore heartbeatStore = new RecordingHeartbeatStore();
        heartbeatStore.managerSnapshots.add(managerSnapshot("server-a", "ctr_01", managedProcess(4096, "http://10.8.0.21:4096")));
        RecordingWeakHealthHttpClient healthClient = new RecordingWeakHealthHttpClient(false, "HTTP 503");
        OpencodeProcessStatusQueryService service = new OpencodeProcessStatusQueryService(
                repository,
                gateway,
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new OpencodeServerAddressResolver("10.8.0.21"),
                healthClient);

        OpencodeProcessWeakHealthResponse response = service.weakHealth(
                new OpencodeProcessWeakHealthRequest("server-a", "ctr_01", 4096),
                TRACE_ID);

        assertThat(response.healthy()).isFalse();
        assertThat(response.status()).isEqualTo(OpencodeProcessWeakHealthStatus.UNHEALTHY);
        assertThat(response.serviceStatus()).isEqualTo("NOT_RUNNING");
        assertThat(response.message()).isEqualTo("HTTP 503");
        assertThat(repository.savedProcesses).isEmpty();
        assertThat(gateway.healthCommands).isEmpty();
        assertThat(heartbeatStore.recordedProcessIds).isEmpty();
    }

    @Test
    void jdkWeakHealthHttpClientMapsNon2xxToUnhealthy() {
        StubHttpClient httpClient = StubHttpClient.status(503);
        JdkOpencodeWeakHealthHttpClient client = new JdkOpencodeWeakHealthHttpClient(httpClient);

        OpencodeWeakHealthHttpResult result = client.check("http://10.8.0.21:4096/", TRACE_ID);

        assertThat(result.healthy()).isFalse();
        assertThat(result.message()).isEqualTo("HTTP 503");
        assertThat(httpClient.requests).singleElement().satisfies(request -> {
            assertThat(request.uri().toString()).isEqualTo("http://10.8.0.21:4096/global/health");
            assertThat(request.headers().firstValue("X-Trace-Id")).contains(TRACE_ID);
        });
    }

    @Test
    void jdkWeakHealthHttpClientMapsTimeoutToUnhealthy() {
        StubHttpClient httpClient = StubHttpClient.failure(new HttpTimeoutException("request timed out"));
        JdkOpencodeWeakHealthHttpClient client = new JdkOpencodeWeakHealthHttpClient(httpClient);

        OpencodeWeakHealthHttpResult result = client.check("http://10.8.0.21:4096", TRACE_ID);

        assertThat(result.healthy()).isFalse();
        assertThat(result.message()).contains("request timed out");
        assertThat(httpClient.requests).hasSize(1);
    }

    private static OpencodeProcessStatusQueryService service(
            FakeRepository repository,
            RecordingGateway gateway,
            RecordingHeartbeatStore heartbeatStore) {
        return new OpencodeProcessStatusQueryService(
                repository,
                gateway,
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OpencodeServerProcess process(
            String processId,
            int port,
            OpencodeServerProcessStatus status,
            Long pid) {
        return process(processId, "10.8.0.12", "http://10.8.0.12:" + port, port, status, pid);
    }

    private static OpencodeServerProcess process(
            String processId,
            String linuxServerId,
            String baseUrl,
            int port,
            OpencodeServerProcessStatus status,
            Long pid) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId),
                new UserId("usr_1234567890abcdef"),
                new LinuxServerId(linuxServerId),
                new OpencodeContainerId("ctr_01"),
                port,
                pid,
                baseUrl,
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

    private static ManagerRuntimeSnapshot managerSnapshot(
            String linuxServerId,
            String containerId,
            ManagedOpencodeProcessSnapshot process) {
        return new ManagerRuntimeSnapshot(
                new OpencodeContainer(
                        new OpencodeContainerId(containerId),
                        new LinuxServerId(linuxServerId),
                        "container-" + containerId,
                        4096,
                        4106,
                        10,
                        1,
                        OpencodeContainerStatus.READY,
                        NOW,
                        NOW.minusSeconds(3600),
                        NOW,
                        TRACE_ID),
                new OpencodeContainerManager(
                        new ContainerManagerId("mgr_1234567890abcdef"),
                        new OpencodeContainerId(containerId),
                        new LinuxServerId(linuxServerId),
                        "opencode-manager.v1",
                        ManagerConnectionStatus.CONNECTED,
                        Map.of(),
                        NOW,
                        NOW.minusSeconds(3600),
                        NOW,
                        TRACE_ID),
                List.of(),
                null,
                List.of(process));
    }

    private static ManagedOpencodeProcessSnapshot managedProcess(int port, String baseUrl) {
        return new ManagedOpencodeProcessSnapshot(
                port,
                12345L,
                baseUrl,
                "/data/opencode/session/" + port,
                "/data/opencode/.config/opencode/",
                NOW.minusSeconds(60),
                "opencode serve --port " + port,
                TRACE_ID);
    }

    private static final class RecordingGateway implements OpencodeProcessManagerGateway {
        private final List<OpencodeProcessHealthCommand> healthCommands = new ArrayList<>();
        private OpencodeProcessHealthResult health = OpencodeProcessHealthResult.healthy(11111L, "ok");
        private RuntimeException healthFailure;

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            healthCommands.add(command);
            if (healthFailure != null) {
                throw healthFailure;
            }
            return health;
        }

        @Override public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) { throw new UnsupportedOperationException("startProcess is not used"); }
        @Override public OpencodeProcessControlResult restartProcess(OpencodeProcessControlCommand command) { throw new UnsupportedOperationException("restartProcess is not used"); }
        @Override public OpencodeProcessControlResult stopProcess(OpencodeProcessControlCommand command) { throw new UnsupportedOperationException("stopProcess is not used"); }
    }

    private static final class RecordingHeartbeatStore implements OpencodeProcessHeartbeatStore {
        private final List<OpencodeProcessId> recordedProcessIds = new ArrayList<>();
        private final List<ManagerRuntimeSnapshot> managerSnapshots = new ArrayList<>();

        @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { recordedProcessIds.add(processId); }
        @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) { }
        @Override public void recordBackendSnapshot(com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot snapshot) { }
        @Override public void recordManagerSnapshot(com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot snapshot) { }
        @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
        @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return new LinkedHashSet<>(recordedProcessIds); }
        @Override public List<com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
        @Override public List<com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.copyOf(managerSnapshots); }
        @Override public void cleanupExpiredHeartbeats() { }
    }

    private static final class RecordingWeakHealthHttpClient implements OpencodeWeakHealthHttpClient {
        private final boolean healthy;
        private final String message;
        private final List<String> calls = new ArrayList<>();

        private RecordingWeakHealthHttpClient(boolean healthy, String message) {
            this.healthy = healthy;
            this.message = message;
        }

        @Override
        public OpencodeWeakHealthHttpResult check(String baseUrl, String traceId) {
            calls.add(baseUrl + "|" + traceId);
            return new OpencodeWeakHealthHttpResult(healthy, message);
        }
    }

    private static final class StubHttpClient extends HttpClient {
        private final int status;
        private final IOException failure;
        private final List<HttpRequest> requests = new ArrayList<>();

        private StubHttpClient(int status, IOException failure) {
            this.status = status;
            this.failure = failure;
        }

        static StubHttpClient status(int status) {
            return new StubHttpClient(status, null);
        }

        static StubHttpClient failure(IOException failure) {
            return new StubHttpClient(200, failure);
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return new StubHttpResponse<>((T) "", request, status);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record StubHttpResponse<T>(T body, HttpRequest request, int statusCode) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (left, right) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository {
        private final Map<OpencodeProcessId, OpencodeServerProcess> processes = new LinkedHashMap<>();
        private final Map<OpencodeProcessId, OpencodeServerProcess> savedProcesses = new LinkedHashMap<>();
        private int findProcessCalls;

        @Override public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) { processes.put(process.processId(), process); savedProcesses.put(process.processId(), process); return process; }
        @Override public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) { findProcessCalls++; return Optional.ofNullable(processes.get(processId)); }
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
