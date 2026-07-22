package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeMetricSample;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ContainerRuntimeMetricSample;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.ServerRuntimeMetricSample;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeManagementQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-24T00:00:00Z");
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void overviewAggregatesTopologyAndMergesUserBindings() {
        FakeRepository repository = new FakeRepository();
        LinuxServer linuxServer = linuxServer();
        BackendJavaProcess backendProcess = backendProcess();
        OpencodeContainer container = container();
        OpencodeContainerManager manager = manager();
        OpencodeManagerBackendConnection connection = connection();
        OpencodeServerProcess process = process("ocp_1234567890abcdef", "usr_1234567890abcdef", OpencodeServerProcessStatus.RUNNING);
        UserOpencodeProcessBinding binding = binding(process);
        repository.linuxServers.put(linuxServer.linuxServerId(), linuxServer);
        repository.backendProcesses.put(backendProcess.backendProcessId(), backendProcess);
        repository.containers.put(container.containerId(), container);
        repository.managers.put(manager.managerId(), manager);
        repository.connections.add(connection);
        repository.processes.add(process);
        repository.bindings.put(process.processId(), binding);
        repository.users.put(process.userId(), user(process.userId(), "process-user"));
        RuntimeManagementQueryService service = service(repository, heartbeatFromRepository(repository));

        RuntimeManagementOverview overview = service.overview(
                new OpencodeServerProcessFilter(
                        OpencodeServerProcessStatus.RUNNING,
                        new LinuxServerId("10.8.0.12"),
                        new OpencodeContainerId("ctr_01"),
                        new UserId("usr_1234567890abcdef")),
                new PageRequest(1, 20),
                TRACE_ID);

        assertThat(overview.generatedAt()).isEqualTo(NOW);
        assertThat(overview.summary().linuxServers()).isEqualTo(1);
        assertThat(overview.summary().readyBackendProcesses()).isEqualTo(1);
        assertThat(overview.summary().connectedManagers()).isEqualTo(1);
        assertThat(overview.summary().runningOpencodeProcesses()).isEqualTo(1);
        assertThat(overview.summary().userBindings()).isEqualTo(1);
        assertThat(overview.linuxServers()).containsExactly(linuxServer);
        assertThat(overview.backendProcesses()).extracting(RuntimeManagementBackendProcess::process).containsExactly(backendProcess);
        assertThat(overview.containers()).extracting(RuntimeManagementContainer::container).containsExactly(container);
        assertThat(overview.managers()).extracting(RuntimeManagementManager::manager).containsExactly(manager);
        assertThat(overview.managerBackendConnections()).containsExactly(connection);
        assertThat(overview.opencodeProcesses().items()).hasSize(1);
        assertThat(overview.opencodeProcesses().items().getFirst().process()).isEqualTo(process);
        assertThat(overview.opencodeProcesses().items().getFirst().binding()).contains(binding);
        assertThat(overview.opencodeProcesses().items().getFirst().username()).contains("process-user");
        assertThat(repository.lastFilter.status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
        assertThat(repository.lastFilter.userId()).isEqualTo(new UserId("usr_1234567890abcdef"));
        assertThat(overview.opencodeProcesses().size()).isEqualTo(20);
    }

    @Test
    void overviewHandlesEmptyTopology() {
        FakeRepository repository = new FakeRepository();
        RuntimeManagementQueryService service = service(repository, new RedisSnapshotHeartbeatStore());

        RuntimeManagementOverview overview = service.overview(OpencodeServerProcessFilter.empty(), new PageRequest(1, 20), TRACE_ID);

        assertThat(overview.summary().linuxServers()).isZero();
        assertThat(overview.summary().opencodeProcesses()).isZero();
        assertThat(overview.opencodeProcesses().items()).isEmpty();
        assertThat(overview.opencodeProcesses().total()).isZero();
    }

    @Test
    void overviewPassesThroughBackendAndManagerBuildVersions() {
        FakeRepository repository = new FakeRepository();
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        heartbeatStore.backendSnapshots.add(new BackendRuntimeSnapshot(
                linuxServer(), backendProcess(), null, "V20260715.090203"));
        heartbeatStore.managerSnapshots.add(new ManagerRuntimeSnapshot(
                container(), manager(), List.of(connection()), null, List.of(), "V20260715.090304"));
        RuntimeManagementQueryService service = service(repository, heartbeatStore);

        RuntimeManagementOverview overview = service.overview(
                OpencodeServerProcessFilter.empty(), new PageRequest(1, 20), TRACE_ID);

        assertThat(overview.backendProcesses()).singleElement().satisfies(row ->
                assertThat(row.buildVersion()).isEqualTo("V20260715.090203"));
        assertThat(overview.managers()).singleElement().satisfies(row ->
                assertThat(row.buildVersion()).isEqualTo("V20260715.090304"));
    }

    @Test
    void userProcessesReturnsStoppedProcessWithoutHeartbeatAndMarksRestartable() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess stopped = process(
                "ocp_1234567890abcdef",
                "usr_1234567890abcdef",
                OpencodeServerProcessStatus.STOPPED);
        repository.processes.add(stopped);
        repository.bindings.put(stopped.processId(), binding(stopped));
        repository.users.put(stopped.userId(), user(stopped.userId(), "process-user"));
        FakeGateway gateway = new FakeGateway();
        gateway.healthResults.put(stopped.processId(), OpencodeProcessHealthResult.unhealthy("process pid is not alive"));
        RuntimeManagementQueryService service = service(repository, new RedisSnapshotHeartbeatStore(), gateway);

        PageResponse<RuntimeManagementOpencodeProcess> page =
                service.userProcesses("process-user", new PageRequest(1, 20), TRACE_ID);

        assertThat(page.items()).hasSize(1);
        RuntimeManagementOpencodeProcess row = page.items().getFirst();
        assertThat(row.process().processId()).isEqualTo(stopped.processId());
        assertThat(row.process().status()).isEqualTo(OpencodeServerProcessStatus.STOPPED);
        assertThat(row.managerStatus()).isEqualTo("NOT_RUNNING");
        assertThat(row.healthStatus()).isEqualTo("NOT_RUNNING");
        assertThat(row.restartable()).isTrue();
        assertThat(row.username()).contains("process-user");
        assertThat(gateway.healthCommands).hasSize(1);
    }

    @Test
    void userProcessesMarksHealthyResultRunningAndRefreshesSnapshot() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess unhealthy = process(
                "ocp_1234567890abcdef",
                "usr_1234567890abcdef",
                OpencodeServerProcessStatus.UNHEALTHY);
        repository.processes.add(unhealthy);
        repository.bindings.put(unhealthy.processId(), binding(unhealthy));
        repository.users.put(unhealthy.userId(), user(unhealthy.userId(), "process-user"));
        FakeGateway gateway = new FakeGateway();
        gateway.healthResults.put(
                unhealthy.processId(),
                OpencodeProcessHealthResult.healthy(unhealthy.pid(), "ok"));
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        RuntimeManagementQueryService service = service(repository, heartbeatStore, gateway);

        PageResponse<RuntimeManagementOpencodeProcess> page =
                service.userProcesses("usr_1234567890abcdef", new PageRequest(1, 20), TRACE_ID);

        RuntimeManagementOpencodeProcess row = page.items().getFirst();
        assertThat(row.process().status()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
        assertThat(row.process().healthMessage()).isEqualTo("ok");
        assertThat(row.managerStatus()).isEqualTo("RUNNING");
        assertThat(row.healthStatus()).isEqualTo("HEALTHY");
        assertThat(row.restartable()).isFalse();
        assertThat(repository.findOpencodeServerProcessById(unhealthy.processId()).orElseThrow().status())
                .isEqualTo(OpencodeServerProcessStatus.RUNNING);
        assertThat(heartbeatStore.liveOpencodeProcessIds()).contains(unhealthy.processId());
    }

    @Test
    void userProcessesSkipsManagerHealthWhenProcessBelongsToRemoteServer() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess remote = process(
                "ocp_1234567890abcdef",
                "usr_1234567890abcdef",
                OpencodeServerProcessStatus.RUNNING);
        repository.processes.add(remote);
        repository.bindings.put(remote.processId(), binding(remote));
        repository.users.put(remote.userId(), user(remote.userId(), "process-user"));
        FakeGateway gateway = new FakeGateway();
        RuntimeManagementQueryService service = new RuntimeManagementQueryService(
                repository,
                repository,
                gateway,
                new RedisSnapshotHeartbeatStore(),
                routeResolver("10.8.0.21"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        PageResponse<RuntimeManagementOpencodeProcess> page =
                service.userProcesses("process-user", new PageRequest(1, 20), TRACE_ID);

        RuntimeManagementOpencodeProcess row = page.items().getFirst();
        assertThat(row.process()).isEqualTo(remote);
        assertThat(row.managerStatus()).isEqualTo("REMOTE_SERVER");
        assertThat(row.healthStatus()).isEqualTo("CHECK_SKIPPED");
        assertThat(row.restartable()).isTrue();
        assertThat(gateway.healthCommands).isEmpty();
    }

    @Test
    void userProcessesResolvesUnifiedAuthKeyword() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess process = process(
                "ocp_1234567890abcdef",
                "usr_1234567890abcdef",
                OpencodeServerProcessStatus.FAILED);
        repository.processes.add(process);
        repository.users.put(process.userId(), user(process.userId(), "process-user"));
        RuntimeManagementQueryService service = service(repository, new RedisSnapshotHeartbeatStore(), new FakeGateway());

        PageResponse<RuntimeManagementOpencodeProcess> page =
                service.userProcesses("AUTH_process-user", new PageRequest(1, 20), TRACE_ID);

        assertThat(page.items()).extracting(row -> row.process().processId()).containsExactly(process.processId());
    }

    @Test
    void metricHistoryReadsRedisSamplesAndDownsamplesByTimeBucket() {
        FakeRepository repository = new FakeRepository();
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        heartbeatStore.containerSamples.add(new ContainerRuntimeMetricSample(
                NOW.minusSeconds(3600),
                4,
                1,
                "cgroup",
                10.0,
                100L,
                20L,
                20.0,
                100.0,
                200.0));
        heartbeatStore.containerSamples.add(new ContainerRuntimeMetricSample(
                NOW.minusSeconds(1800),
                4,
                3,
                "process",
                30.0,
                100L,
                60L,
                60.0,
                300.0,
                600.0));
        RuntimeManagementQueryService service = service(repository, heartbeatStore);

        RuntimeManagementContainerMetricHistory history = service.containerMetrics(
                new OpencodeContainerId("ctr_01"),
                Duration.ofHours(1),
                1,
                TRACE_ID);

        assertThat(history.samples()).hasSize(1);
        RuntimeManagementContainerMetricSample sample = history.samples().getFirst();
        assertThat(sample.cpuUsagePercent()).isEqualTo(20.0);
        assertThat(sample.currentProcesses()).isEqualTo(3);
        assertThat(sample.metricsSource()).isEqualTo("process");
        assertThat(sample.memoryUsedBytes()).isEqualTo(40L);
    }

    @Test
    void metricHistoryUsesMinuteWindowForRedisQueryRange() {
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        RuntimeManagementQueryService service = service(new FakeRepository(), heartbeatStore);

        RuntimeManagementContainerMetricHistory history = service.containerMetrics(
                new OpencodeContainerId("ctr_01"),
                Duration.ofMinutes(1),
                720,
                TRACE_ID);

        assertThat(history.from()).isEqualTo(NOW.minus(Duration.ofMinutes(1)));
        assertThat(history.to()).isEqualTo(NOW);
        assertThat(heartbeatStore.lastContainerFrom).isEqualTo(NOW.minus(Duration.ofMinutes(1)));
        assertThat(heartbeatStore.lastContainerTo).isEqualTo(NOW);
    }

    @Test
    void backendMetricHistoryUsesLinuxServerIdForServerAndJvmSamplesAcrossBackendProcessRestart() {
        FakeRepository repository = new FakeRepository();
        BackendJavaProcess staleBackend = new BackendJavaProcess(
                new BackendProcessId("bjp_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                "http://10.8.0.12:8080",
                BackendJavaProcessStatus.READY,
                NOW.minusSeconds(30),
                NOW.minusSeconds(30),
                NOW.minusSeconds(30),
                NOW.minusSeconds(30),
                TRACE_ID);
        BackendJavaProcess currentBackend = new BackendJavaProcess(
                new BackendProcessId("bjp_2234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                "http://10.8.0.12:8080",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
        repository.backendProcesses.put(staleBackend.backendProcessId(), staleBackend);
        repository.backendProcesses.put(currentBackend.backendProcessId(), currentBackend);
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        heartbeatStore.backendSnapshots.add(new BackendRuntimeSnapshot(linuxServer(), staleBackend));
        heartbeatStore.backendSnapshots.add(new BackendRuntimeSnapshot(linuxServer(), currentBackend));
        heartbeatStore.serverSamples.add(new ServerRuntimeMetricSample(
                NOW.minusSeconds(40),
                55.0,
                8,
                1.5,
                1.2,
                0.8,
                1600L,
                1600L,
                900L,
                800L,
                700L,
                43.75,
                64L,
                256L,
                1000L,
                800L,
                200L,
                20.0,
                10000L,
                4000L,
                6000L,
                60.0));
        heartbeatStore.backendSamples.add(backendOnlySample(NOW.minusSeconds(20)));
        RuntimeManagementQueryService service = service(repository, heartbeatStore);

        RuntimeManagementBackendMetricHistory history = service.backendServerMetrics(
                new LinuxServerId("10.8.0.12"),
                Duration.ofMinutes(1),
                720,
                TRACE_ID);

        assertThat(history.linuxServerId()).isEqualTo(new LinuxServerId("10.8.0.12"));
        assertThat(history.backendProcessId()).contains(currentBackend.backendProcessId());
        assertThat(heartbeatStore.lastServerLinuxServerId).isEqualTo(new LinuxServerId("10.8.0.12"));
        assertThat(heartbeatStore.lastBackendLinuxServerId).isEqualTo(new LinuxServerId("10.8.0.12"));
        assertThat(history.samples()).hasSize(2);
        RuntimeManagementBackendMetricSample serverSample = history.samples().get(0);
        assertThat(serverSample.cpuUsagePercent()).isEqualTo(55.0);
        assertThat(serverSample.memoryAvailableBytes()).isEqualTo(900L);
        assertThat(serverSample.swapUsagePercent()).isEqualTo(20.0);
        assertThat(serverSample.diskUsagePercent()).isEqualTo(60.0);
        assertThat(serverSample.jvmThreadsLive()).isNull();
        RuntimeManagementBackendMetricSample jvmSample = history.samples().get(1);
        assertThat(jvmSample.cpuUsagePercent()).isNull();
        assertThat(jvmSample.jvmProcessResidentMemoryBytes()).isEqualTo(700L);
        assertThat(jvmSample.jvmHeapUsedBytes()).isEqualTo(200L);
        assertThat(jvmSample.jvmGcCollectionCountDelta()).isEqualTo(3L);
        assertThat(jvmSample.jvmThreadsDaemon()).isEqualTo(12);
        assertThat(jvmSample.jvmThreadsLive()).isEqualTo(42);
    }

    @Test
    void backendMetricHistoryFallsBackToLegacyBackendSamplesWhenServerHistoryMissing() {
        FakeRepository repository = new FakeRepository();
        repository.backendProcesses.put(backendProcess().backendProcessId(), backendProcess());
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        heartbeatStore.backendSamples.add(new BackendRuntimeMetricSample(
                NOW.minusSeconds(20),
                33.0,
                1600L,
                800L,
                50.0,
                10000L,
                6000L,
                60.0,
                300L,
                400L,
                500L,
                7L,
                42));
        RuntimeManagementQueryService service = service(repository, heartbeatStore);

        RuntimeManagementBackendMetricHistory history = service.backendProcessMetrics(
                backendProcess().backendProcessId(),
                Duration.ofMinutes(1),
                720,
                TRACE_ID);

        assertThat(history.linuxServerId()).isEqualTo(backendProcess().linuxServerId());
        assertThat(history.backendProcessId()).contains(backendProcess().backendProcessId());
        assertThat(history.samples()).hasSize(1);
        assertThat(history.samples().getFirst().cpuUsagePercent()).isEqualTo(33.0);
        assertThat(history.samples().getFirst().jvmThreadsLive()).isEqualTo(42);
    }

    @Test
    void overviewUsesRedisRuntimeSnapshotsInsteadOfDatabaseHeartbeatFields() {
        FakeRepository repository = new FakeRepository();
        LinuxServer dbStaleServer = new LinuxServer(
                new LinuxServerId("10.8.0.99"),
                "10.8.0.99",
                LinuxServerStatus.READY,
                Map.of(),
                NOW.minusSeconds(600),
                NOW.minusSeconds(600),
                NOW.minusSeconds(600),
                TRACE_ID);
        repository.linuxServers.put(dbStaleServer.linuxServerId(), dbStaleServer);
        OpencodeServerProcess liveProcess = process("ocp_1234567890abcdef", "usr_1234567890abcdef", OpencodeServerProcessStatus.RUNNING);
        repository.processes.add(liveProcess);
        repository.users.put(liveProcess.userId(), user(liveProcess.userId(), "process-user"));

        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        heartbeatStore.backendSnapshots.add(new BackendRuntimeSnapshot(linuxServer(), backendProcess()));
        heartbeatStore.managerSnapshots.add(new ManagerRuntimeSnapshot(
                container(),
                manager(),
                List.of(connection())));
        heartbeatStore.liveOpencodeProcessIds.add(liveProcess.processId());
        RuntimeManagementQueryService service = new RuntimeManagementQueryService(
                repository,
                repository,
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RuntimeManagementOverview overview = service.overview(
                OpencodeServerProcessFilter.empty(),
                new PageRequest(1, 20),
                TRACE_ID);

        assertThat(overview.linuxServers()).containsExactly(linuxServer());
        assertThat(overview.backendProcesses()).extracting(RuntimeManagementBackendProcess::process).containsExactly(backendProcess());
        assertThat(overview.containers()).extracting(RuntimeManagementContainer::container).containsExactly(container());
        assertThat(overview.managers()).extracting(RuntimeManagementManager::manager).containsExactly(manager());
        assertThat(overview.managerBackendConnections()).containsExactly(connection());
        assertThat(overview.opencodeProcesses().items()).extracting(row -> row.process().processId())
                .containsExactly(liveProcess.processId());
        assertThat(overview.summary().readyBackendProcesses()).isEqualTo(1);
        assertThat(overview.summary().connectedManagers()).isEqualTo(1);
    }

    @Test
    void overviewPreservesManagedProcessesUnderManagerRows() {
        FakeRepository repository = new FakeRepository();
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        heartbeatStore.managerSnapshots.add(new ManagerRuntimeSnapshot(
                container(),
                manager(),
                List.of(connection()),
                null,
                List.of(managedProcess())));
        RuntimeManagementQueryService service = service(repository, heartbeatStore);

        RuntimeManagementOverview overview =
                service.overview(OpencodeServerProcessFilter.empty(), new PageRequest(1, 20), TRACE_ID);

        RuntimeManagementManager row = overview.managers().getFirst();
        assertThat(row.manager()).isEqualTo(manager());
        assertThat(row.managedProcesses()).singleElement().satisfies(process -> {
            assertThat(process.port()).isEqualTo(4096);
            assertThat(process.startCommand()).contains("opencode serve --hostname 0.0.0.0 --port 4096");
            assertThat(process.unifiedAuthId()).isEqualTo("A");
            assertThat(process.managerStatus()).isEqualTo("PID_ALIVE");
        });
    }

    @Test
    void overviewKeepsManagedProcessMetadataNullForLegacySnapshot() {
        FakeRepository repository = new FakeRepository();
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        heartbeatStore.managerSnapshots.add(new ManagerRuntimeSnapshot(
                container(),
                manager(),
                List.of(connection()),
                null,
                List.of(legacyManagedProcess())));
        RuntimeManagementQueryService service = service(repository, heartbeatStore);

        RuntimeManagementManagedProcess row = service
                .overview(OpencodeServerProcessFilter.empty(), new PageRequest(1, 20), TRACE_ID)
                .managers()
                .getFirst()
                .managedProcesses()
                .getFirst();

        assertThat(row.unifiedAuthId()).isNull();
        assertThat(row.managerStatus()).isNull();
    }

    @Test
    void overviewMarksManagedProcessesWithActiveBindingAsBound() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess process = process("ocp_1234567890abcdef", "usr_1234567890abcdef", OpencodeServerProcessStatus.RUNNING);
        repository.processes.add(process);
        repository.bindings.put(process.processId(), binding(process));
        repository.users.put(process.userId(), user(process.userId(), "process-user"));
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        heartbeatStore.managerSnapshots.add(new ManagerRuntimeSnapshot(
                container(),
                manager(),
                List.of(connection()),
                null,
                List.of(managedProcess())));
        RuntimeManagementQueryService service = service(repository, heartbeatStore);

        RuntimeManagementOverview overview = service.overview(
                OpencodeServerProcessFilter.byUsername("missing-user"),
                new PageRequest(1, 1),
                TRACE_ID);

        RuntimeManagementManagedProcess row = overview.managers().getFirst().managedProcesses().getFirst();
        assertThat(overview.opencodeProcesses().items()).isEmpty();
        assertThat(row.ownership()).isEqualTo(RuntimeManagementManagedProcessOwnership.BOUND);
        assertThat(row.processId()).isEqualTo(process.processId());
        assertThat(row.processStatus()).isEqualTo(OpencodeServerProcessStatus.RUNNING);
        assertThat(row.unifiedAuthId()).isEqualTo("A");
        assertThat(row.managerStatus()).isEqualTo("PID_ALIVE");
        assertThat(row.userId()).isEqualTo(process.userId());
        assertThat(row.username()).contains("process-user");
        assertThat(row.bindingStatus()).isEqualTo(UserOpencodeProcessBindingStatus.ACTIVE);
    }

    @Test
    void overviewMarksManagedProcessesWithoutActiveBindingAsUnbound() {
        FakeRepository repository = new FakeRepository();
        OpencodeServerProcess process = process("ocp_1234567890abcdef", "usr_1234567890abcdef", OpencodeServerProcessStatus.UNHEALTHY);
        repository.processes.add(process);
        repository.bindings.put(process.processId(), new UserOpencodeProcessBinding(
                process.userId(),
                "opencode",
                process.processId(),
                process.linuxServerId(),
                process.port(),
                UserOpencodeProcessBindingStatus.INACTIVE,
                NOW,
                NOW,
                TRACE_ID));
        repository.users.put(process.userId(), user(process.userId(), "process-user"));
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        heartbeatStore.managerSnapshots.add(new ManagerRuntimeSnapshot(
                container(),
                manager(),
                List.of(connection()),
                null,
                List.of(managedProcess())));
        RuntimeManagementQueryService service = service(repository, heartbeatStore);

        RuntimeManagementManagedProcess row = service
                .overview(OpencodeServerProcessFilter.empty(), new PageRequest(1, 20), TRACE_ID)
                .managers()
                .getFirst()
                .managedProcesses()
                .getFirst();

        assertThat(row.ownership()).isEqualTo(RuntimeManagementManagedProcessOwnership.UNBOUND);
        assertThat(row.processId()).isEqualTo(process.processId());
        assertThat(row.processStatus()).isEqualTo(OpencodeServerProcessStatus.UNHEALTHY);
        assertThat(row.healthMessage()).isEqualTo("ok");
        assertThat(row.userId()).isNull();
        assertThat(row.username()).isEmpty();
        assertThat(row.bindingStatus()).isNull();
    }

    @Test
    void overviewReturnsEmptyRuntimeRowsWhenRedisHasNoSnapshots() {
        RuntimeManagementQueryService service = new RuntimeManagementQueryService(
                new FakeRepository(),
                new FakeRepository(),
                disabledHeartbeatStore(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        RuntimeManagementOverview overview =
                service.overview(OpencodeServerProcessFilter.empty(), new PageRequest(1, 20), TRACE_ID);

        assertThat(overview.linuxServers()).isEmpty();
        assertThat(overview.backendProcesses()).isEmpty();
        assertThat(overview.containers()).isEmpty();
        assertThat(overview.managers()).isEmpty();
    }

    @Test
    void overviewCountsRunningProcessesAcrossAllPages() {
        FakeRepository repository = new FakeRepository();
        repository.processes.add(process("ocp_1111111111111111", "usr_1111111111111111", OpencodeServerProcessStatus.RUNNING));
        repository.processes.add(process("ocp_2222222222222222", "usr_2222222222222222", OpencodeServerProcessStatus.RUNNING));
        repository.processes.add(process("ocp_3333333333333333", "usr_3333333333333333", OpencodeServerProcessStatus.STOPPED));
        RuntimeManagementQueryService service = service(repository, heartbeatFromRepository(repository));

        RuntimeManagementOverview overview = service.overview(OpencodeServerProcessFilter.empty(), new PageRequest(1, 1), TRACE_ID);

        assertThat(overview.opencodeProcesses().items()).hasSize(1);
        assertThat(overview.opencodeProcesses().total()).isEqualTo(2);
        assertThat(overview.summary().runningOpencodeProcesses()).isEqualTo(2);
    }

    @Test
    void overviewReturnsZeroRunningCountWhenStatusFilterExcludesRunning() {
        FakeRepository repository = new FakeRepository();
        repository.processes.add(process("ocp_1111111111111111", "usr_1111111111111111", OpencodeServerProcessStatus.RUNNING));
        repository.processes.add(process("ocp_2222222222222222", "usr_2222222222222222", OpencodeServerProcessStatus.FAILED));
        RuntimeManagementQueryService service = service(repository, heartbeatFromRepository(repository));

        RuntimeManagementOverview overview = service.overview(
                new OpencodeServerProcessFilter(OpencodeServerProcessStatus.FAILED, null, null, null),
                new PageRequest(1, 20),
                TRACE_ID);

        assertThat(overview.opencodeProcesses().items()).isEmpty();
        assertThat(overview.summary().runningOpencodeProcesses()).isZero();
    }

    @Test
    void overviewShowsOnlyLiveRuntimeProcessesAndResolvesUsernameFilter() {
        FakeRepository repository = new FakeRepository();
        LinuxServer liveServer = linuxServer();
        LinuxServer staleServer = new LinuxServer(
                new LinuxServerId("10.8.0.13"),
                "10.8.0.13",
                LinuxServerStatus.READY,
                Map.of(),
                NOW.minusSeconds(301),
                NOW.minusSeconds(600),
                NOW.minusSeconds(301),
                TRACE_ID);
        BackendJavaProcess liveBackend = backendProcess();
        BackendJavaProcess staleBackend = new BackendJavaProcess(
                new BackendProcessId("bjp_2234567890abcdef"),
                staleServer.linuxServerId(),
                "http://10.8.0.13:8080",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW.minusSeconds(301),
                NOW.minusSeconds(600),
                NOW.minusSeconds(301),
                TRACE_ID);
        OpencodeServerProcess liveProcess = process("ocp_1234567890abcdef", "usr_1234567890abcdef", OpencodeServerProcessStatus.RUNNING);
        OpencodeServerProcess staleProcess = new OpencodeServerProcess(
                new OpencodeProcessId("ocp_2234567890abcdef"),
                new UserId("usr_stale_1234567890"),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4097,
                12346L,
                "http://10.8.0.12:4097",
                OpencodeServerProcessStatus.RUNNING,
                "/data/opencode/session/4097",
                "/data/opencode/.config/opencode/",
                NOW,
                NOW.minusSeconds(301),
                "ok",
                NOW.minusSeconds(600),
                NOW.minusSeconds(301),
                TRACE_ID);
        repository.linuxServers.put(liveServer.linuxServerId(), liveServer);
        repository.linuxServers.put(staleServer.linuxServerId(), staleServer);
        repository.backendProcesses.put(liveBackend.backendProcessId(), liveBackend);
        repository.backendProcesses.put(staleBackend.backendProcessId(), staleBackend);
        repository.processes.add(liveProcess);
        repository.processes.add(staleProcess);
        repository.users.put(liveProcess.userId(), user(liveProcess.userId(), "wr"));
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        heartbeatStore.backendSnapshots.add(new BackendRuntimeSnapshot(liveServer, liveBackend));
        heartbeatStore.liveOpencodeProcessIds.add(liveProcess.processId());
        RuntimeManagementQueryService service = service(repository, heartbeatStore);

        RuntimeManagementOverview overview = service.overview(
                OpencodeServerProcessFilter.byUsername("wr"),
                new PageRequest(1, 20),
                TRACE_ID);

        assertThat(overview.linuxServers()).containsExactly(liveServer);
        assertThat(overview.backendProcesses()).extracting(RuntimeManagementBackendProcess::process).containsExactly(liveBackend);
        assertThat(overview.opencodeProcesses().items()).extracting(row -> row.process().processId())
                .containsExactly(liveProcess.processId());
        assertThat(overview.opencodeProcesses().total()).isEqualTo(1);
        assertThat(overview.summary().opencodeProcesses()).isEqualTo(1);
        assertThat(overview.summary().runningOpencodeProcesses()).isEqualTo(1);
        assertThat(repository.lastFilter.userId()).isEqualTo(liveProcess.userId());
    }


    private static LinuxServer linuxServer() {
        return new LinuxServer(
                new LinuxServerId("10.8.0.12"),
                "10.8.0.12",
                LinuxServerStatus.READY,
                Map.of("cpu", 4),
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static BackendJavaProcess backendProcess() {
        return new BackendJavaProcess(
                new BackendProcessId("bjp_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                "http://10.8.0.12:8080",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static OpencodeContainer container() {
        return new OpencodeContainer(
                new OpencodeContainerId("ctr_01"),
                new LinuxServerId("10.8.0.12"),
                "opencode-a",
                4096,
                4100,
                4,
                1,
                OpencodeContainerStatus.READY,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static OpencodeContainerManager manager() {
        return new OpencodeContainerManager(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new LinuxServerId("10.8.0.12"),
                "opencode-manager.v1",
                ManagerConnectionStatus.CONNECTED,
                Map.of("start", true),
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static ManagedOpencodeProcessSnapshot managedProcess() {
        return new ManagedOpencodeProcessSnapshot(
                4096,
                12345L,
                "http://10.8.0.12:4096",
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                NOW,
                "XDG_DATA_HOME=/data/opencode/session/4096 OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs",
                TRACE_ID,
                "A",
                "PID_ALIVE");
    }

    private static ManagedOpencodeProcessSnapshot legacyManagedProcess() {
        return new ManagedOpencodeProcessSnapshot(
                4096,
                12345L,
                "http://10.8.0.12:4096",
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                NOW,
                "XDG_DATA_HOME=/data/opencode/session/4096 OPENCODE_CONFIG_DIR=/data/opencode/.config/opencode/ opencode serve --hostname 0.0.0.0 --port 4096 --print-logs",
                TRACE_ID);
    }

    private static OpencodeManagerBackendConnection connection() {
        return new OpencodeManagerBackendConnection(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new BackendProcessId("bjp_1234567890abcdef"),
                ManagerConnectionStatus.CONNECTED,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static OpencodeServerProcess process(String processId, String userId, OpencodeServerProcessStatus status) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId),
                new UserId(userId),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                4096,
                12345L,
                "http://10.8.0.12:4096",
                status,
                "/data/opencode/session/4096",
                "/data/opencode/.config/opencode/",
                NOW,
                NOW,
                "ok",
                NOW,
                NOW,
                TRACE_ID);
    }

    private static UserOpencodeProcessBinding binding(OpencodeServerProcess process) {
        return new UserOpencodeProcessBinding(
                process.userId(),
                "opencode",
                process.processId(),
                process.linuxServerId(),
                process.port(),
                UserOpencodeProcessBindingStatus.ACTIVE,
                NOW,
                NOW,
                TRACE_ID);
    }

    private static User user(UserId userId, String username) {
        return new User(
                userId,
                "AUTH_" + username,
                username,
                "hash",
                null,
                null,
                null,
                UserStatus.ACTIVE,
                NOW,
                NOW);
    }

    private static RuntimeManagementQueryService service(
            FakeRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        return service(repository, heartbeatStore, new FakeGateway());
    }

    private static RuntimeManagementQueryService service(
            FakeRepository repository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            OpencodeProcessManagerGateway gateway) {
        return new RuntimeManagementQueryService(
                repository,
                repository,
                gateway,
                heartbeatStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BackendJavaRouteResolver routeResolver(String currentLinuxServerId) {
        return new BackendJavaRouteResolver(
                disabledHeartbeatStore(),
                new ManagerControlSettings(
                        "manager-token",
                        "http://" + currentLinuxServerId + ":8080",
                        new LinuxServerId(currentLinuxServerId),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(10),
                        100),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RedisSnapshotHeartbeatStore heartbeatFromRepository(FakeRepository repository) {
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        for (BackendJavaProcess backendProcess : repository.backendProcesses.values()) {
            LinuxServer linuxServer = repository.linuxServers.get(backendProcess.linuxServerId());
            if (linuxServer != null) {
                heartbeatStore.backendSnapshots.add(new BackendRuntimeSnapshot(linuxServer, backendProcess));
            }
        }
        for (OpencodeContainerManager manager : repository.managers.values()) {
            OpencodeContainer container = repository.containers.get(manager.containerId());
            if (container != null) {
                List<OpencodeManagerBackendConnection> connections = repository.connections.stream()
                        .filter(connection -> connection.managerId().equals(manager.managerId()))
                        .toList();
                heartbeatStore.managerSnapshots.add(new ManagerRuntimeSnapshot(container, manager, connections));
            }
        }
        repository.processes.stream()
                .filter(process -> process.status() == OpencodeServerProcessStatus.RUNNING)
                .map(OpencodeServerProcess::processId)
                .forEach(heartbeatStore.liveOpencodeProcessIds::add);
        return heartbeatStore;
    }

    private static final class FakeRepository implements OpencodeProcessManagementRepository, UserRepository {
        private final Map<LinuxServerId, LinuxServer> linuxServers = new LinkedHashMap<>();
        private final Map<BackendProcessId, BackendJavaProcess> backendProcesses = new LinkedHashMap<>();
        private final Map<OpencodeContainerId, OpencodeContainer> containers = new LinkedHashMap<>();
        private final Map<ContainerManagerId, OpencodeContainerManager> managers = new LinkedHashMap<>();
        private final List<OpencodeManagerBackendConnection> connections = new java.util.ArrayList<>();
        private final List<OpencodeServerProcess> processes = new java.util.ArrayList<>();
        private final Map<OpencodeProcessId, UserOpencodeProcessBinding> bindings = new LinkedHashMap<>();
        private final Map<UserId, User> users = new LinkedHashMap<>();
        private OpencodeServerProcessFilter lastFilter;
        private PageRequest lastPageRequest;

        @Override public List<LinuxServer> findLinuxServers(int limit) { return linuxServers.values().stream().limit(limit).toList(); }
        @Override public List<BackendJavaProcess> findBackendJavaProcesses(int limit) { return backendProcesses.values().stream().limit(limit).toList(); }
        @Override public List<OpencodeContainer> findContainers(int limit) { return containers.values().stream().limit(limit).toList(); }
        @Override public List<OpencodeContainerManager> findContainerManagers(int limit) { return managers.values().stream().limit(limit).toList(); }
        @Override public List<OpencodeManagerBackendConnection> findManagerBackendConnections(int limit) { return connections.stream().limit(limit).toList(); }
        @Override public long countUserBindings() { return bindings.size(); }

        @Override
        public PageResponse<OpencodeServerProcess> findOpencodeServerProcesses(
                OpencodeServerProcessFilter filter,
                PageRequest pageRequest) {
            lastFilter = filter;
            lastPageRequest = pageRequest;
            List<OpencodeServerProcess> filtered = filterProcesses(filter);
            return new PageResponse<>(
                    filtered.stream()
                            .skip(pageRequest.offset())
                            .limit(pageRequest.size())
                            .toList(),
                    pageRequest.page(),
                    pageRequest.size(),
                    filtered.size());
        }

        @Override
        public long countOpencodeServerProcesses(OpencodeServerProcessFilter filter) {
            return filterProcesses(filter).size();
        }

        @Override
        public Map<OpencodeProcessId, UserOpencodeProcessBinding> findUserBindingsByProcessIds(List<OpencodeProcessId> processIds) {
            Map<OpencodeProcessId, UserOpencodeProcessBinding> found = new LinkedHashMap<>();
            for (OpencodeProcessId processId : processIds) {
                UserOpencodeProcessBinding binding = bindings.get(processId);
                if (binding != null) {
                    found.put(processId, binding);
                }
            }
            return found;
        }

        @Override public LinuxServer saveLinuxServer(LinuxServer linuxServer) { linuxServers.put(linuxServer.linuxServerId(), linuxServer); return linuxServer; }
        @Override public Optional<LinuxServer> findLinuxServerById(LinuxServerId linuxServerId) { return Optional.ofNullable(linuxServers.get(linuxServerId)); }
        @Override public BackendJavaProcess saveBackendJavaProcess(BackendJavaProcess backendJavaProcess) { backendProcesses.put(backendJavaProcess.backendProcessId(), backendJavaProcess); return backendJavaProcess; }
        @Override public Optional<BackendJavaProcess> findBackendJavaProcessById(BackendProcessId backendProcessId) { return Optional.ofNullable(backendProcesses.get(backendProcessId)); }
        @Override public Optional<BackendJavaProcess> findReadyBackendJavaProcessByLinuxServer(LinuxServerId linuxServerId) { return Optional.empty(); }
        @Override public List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit) { return List.of(); }
        @Override public OpencodeContainer saveContainer(OpencodeContainer container) { containers.put(container.containerId(), container); return container; }
        @Override public Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId) { return Optional.ofNullable(containers.get(containerId)); }
        @Override public List<OpencodeContainer> findHealthyContainers(int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public OpencodeContainerManager saveContainerManager(OpencodeContainerManager manager) { managers.put(manager.managerId(), manager); return manager; }
        @Override public Optional<OpencodeContainerManager> findContainerManagerById(ContainerManagerId managerId) { return Optional.ofNullable(managers.get(managerId)); }
        @Override public OpencodeManagerBackendConnection saveManagerBackendConnection(OpencodeManagerBackendConnection connection) { connections.add(connection); return connection; }
        @Override public Optional<OpencodeManagerBackendConnection> findManagerBackendConnection(ContainerManagerId managerId, BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) {
            processes.removeIf(existing -> existing.processId().equals(process.processId()));
            processes.add(process);
            return process;
        }
        @Override public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) { return processes.stream().filter(process -> process.processId().equals(processId)).findFirst(); }
        @Override public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) { return List.of(); }
        @Override public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) { bindings.put(binding.processId(), binding); return binding; }
        @Override public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) { return Optional.empty(); }
        @Override public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return processes.stream().limit(limit).toList(); }
        @Override public void save(User user) { users.put(user.userId(), user); }
        @Override public Optional<User> findByUserId(UserId userId) { return Optional.ofNullable(users.get(userId)); }
        @Override public Optional<User> findByUnifiedAuthId(String unifiedAuthId) { return users.values().stream().filter(user -> user.unifiedAuthId().equals(unifiedAuthId)).findFirst(); }
        @Override public Optional<User> findByUsername(String username) { return users.values().stream().filter(user -> user.username().equals(username)).findFirst(); }
        @Override public com.enterprise.testagent.common.pagination.PageResponse<User> findPage(String keyword, PageRequest pageRequest) {
            String normalized = keyword == null ? "" : keyword.trim();
            List<User> matched = users.values().stream()
                    .filter(user -> normalized.isBlank()
                            || user.userId().value().contains(normalized)
                            || user.username().contains(normalized)
                            || user.unifiedAuthId().contains(normalized))
                    .skip(pageRequest.offset())
                    .limit(pageRequest.size())
                    .toList();
            long total = users.values().stream()
                    .filter(user -> normalized.isBlank()
                            || user.userId().value().contains(normalized)
                            || user.username().contains(normalized)
                            || user.unifiedAuthId().contains(normalized))
                    .count();
            return new PageResponse<>(matched, pageRequest.page(), pageRequest.size(), total);
        }
        @Override public boolean existsByUsername(String username) { return findByUsername(username).isPresent(); }
        @Override public boolean existsByUnifiedAuthId(String unifiedAuthId) { return findByUnifiedAuthId(unifiedAuthId).isPresent(); }

        private List<OpencodeServerProcess> filterProcesses(OpencodeServerProcessFilter filter) {
            return processes.stream()
                    .filter(process -> filter.status() == null || process.status() == filter.status())
                    .filter(process -> filter.linuxServerId() == null || process.linuxServerId().equals(filter.linuxServerId()))
                    .filter(process -> filter.containerId() == null || process.containerId().equals(filter.containerId()))
                    .filter(process -> filter.userId() == null || process.userId().equals(filter.userId()))
                    .toList();
        }
    }

    private static final class FakeGateway implements OpencodeProcessManagerGateway {
        private final List<OpencodeProcessHealthCommand> healthCommands = new java.util.ArrayList<>();
        private final Map<OpencodeProcessId, OpencodeProcessHealthResult> healthResults = new LinkedHashMap<>();

        @Override
        public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
            healthCommands.add(command);
            return healthResults.getOrDefault(command.processId(), OpencodeProcessHealthResult.unhealthy("process pid is not alive"));
        }

        @Override public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) { throw new UnsupportedOperationException(); }
        @Override public OpencodeProcessControlResult restartProcess(OpencodeProcessControlCommand command) { throw new UnsupportedOperationException(); }
        @Override public OpencodeProcessControlResult stopProcess(OpencodeProcessControlCommand command) { throw new UnsupportedOperationException(); }
    }

    private static OpencodeProcessHeartbeatStore disabledHeartbeatStore() {
        return new OpencodeProcessHeartbeatStore() {
            @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) { }
            @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) { }
            @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) { }
            @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { }
            @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
            @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
            @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.of(); }
            @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
            @Override public void cleanupExpiredHeartbeats() { }
        };
    }

    private static BackendRuntimeMetricSample backendOnlySample(Instant sampledAt) {
        return new BackendRuntimeMetricSample(
                sampledAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                7.5,
                0.6,
                123456789L,
                700L,
                900L,
                4096L,
                32L,
                50L,
                1024L,
                300L,
                400L,
                500L,
                200L,
                300L,
                400L,
                100L,
                100L,
                100L,
                2L,
                16L,
                32L,
                1L,
                8L,
                16L,
                7L,
                7L,
                3L,
                0.4,
                42,
                12,
                48,
                1000L);
    }

    private static final class RedisSnapshotHeartbeatStore implements OpencodeProcessHeartbeatStore {
        private final List<BackendRuntimeSnapshot> backendSnapshots = new java.util.ArrayList<>();
        private final List<ManagerRuntimeSnapshot> managerSnapshots = new java.util.ArrayList<>();
        private final List<ContainerRuntimeMetricSample> containerSamples = new java.util.ArrayList<>();
        private final List<BackendRuntimeMetricSample> backendSamples = new java.util.ArrayList<>();
        private final List<ServerRuntimeMetricSample> serverSamples = new java.util.ArrayList<>();
        private final Set<LinuxServerId> liveBackendServerIds = new LinkedHashSet<>();
        private final Set<OpencodeProcessId> liveOpencodeProcessIds = new LinkedHashSet<>();
        private Instant lastContainerFrom;
        private Instant lastContainerTo;
        private LinuxServerId lastBackendLinuxServerId;
        private LinuxServerId lastServerLinuxServerId;

        @Override public void recordBackendHeartbeat(LinuxServerId linuxServerId, Instant heartbeatAt) {
            liveBackendServerIds.add(linuxServerId);
        }
        @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) {
            backendSnapshots.add(snapshot);
            liveBackendServerIds.add(snapshot.backendProcess().linuxServerId());
        }
        @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) {
            managerSnapshots.add(snapshot);
        }
        @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) {
            liveOpencodeProcessIds.add(processId);
        }
        @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.copyOf(backendSnapshots); }
        @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.copyOf(managerSnapshots); }
        @Override public List<ContainerRuntimeMetricSample> containerMetricSamples(OpencodeContainerId containerId, Instant from, Instant to) {
            lastContainerFrom = from;
            lastContainerTo = to;
            return containerSamples.stream()
                    .filter(sample -> !sample.sampledAt().isBefore(from) && !sample.sampledAt().isAfter(to))
                    .toList();
        }
        @Override public List<BackendRuntimeMetricSample> backendMetricSamples(LinuxServerId linuxServerId, Instant from, Instant to) {
            lastBackendLinuxServerId = linuxServerId;
            return backendSamples.stream()
                    .filter(sample -> !sample.sampledAt().isBefore(from) && !sample.sampledAt().isAfter(to))
                    .toList();
        }
        @Override public List<BackendRuntimeMetricSample> legacyBackendMetricSamples(BackendProcessId backendProcessId, Instant from, Instant to) {
            return backendSamples.stream()
                    .filter(sample -> !sample.sampledAt().isBefore(from) && !sample.sampledAt().isAfter(to))
                    .toList();
        }
        @Override public List<ServerRuntimeMetricSample> serverMetricSamples(LinuxServerId linuxServerId, Instant from, Instant to) {
            lastServerLinuxServerId = linuxServerId;
            return serverSamples.stream()
                    .filter(sample -> !sample.sampledAt().isBefore(from) && !sample.sampledAt().isAfter(to))
                    .toList();
        }
        @Override public Set<LinuxServerId> liveBackendServerIds() { return Set.copyOf(liveBackendServerIds); }
        @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.copyOf(liveOpencodeProcessIds); }
        @Override public void cleanupExpiredHeartbeats() { }
    }
}
