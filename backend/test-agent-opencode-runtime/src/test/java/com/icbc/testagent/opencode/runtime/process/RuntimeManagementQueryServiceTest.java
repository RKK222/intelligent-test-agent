package com.icbc.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeMetricSample;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.ContainerRuntimeMetricSample;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.ServerRuntimeMetricSample;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import com.icbc.testagent.domain.user.UserStatus;
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
        assertThat(overview.managers()).containsExactly(manager);
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
    void backendMetricHistoryKeepsServerMetricsAcrossBackendProcessRestart() {
        FakeRepository repository = new FakeRepository();
        BackendProcessId currentBackendId = new BackendProcessId("bjp_2234567890abcdef");
        BackendJavaProcess currentBackend = new BackendJavaProcess(
                currentBackendId,
                new LinuxServerId("10.8.0.12"),
                "http://10.8.0.12:8080",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
        repository.backendProcesses.put(currentBackendId, currentBackend);
        RedisSnapshotHeartbeatStore heartbeatStore = new RedisSnapshotHeartbeatStore();
        heartbeatStore.backendSnapshots.add(new BackendRuntimeSnapshot(linuxServer(), currentBackend));
        heartbeatStore.serverSamples.add(new ServerRuntimeMetricSample(
                NOW.minusSeconds(40),
                55.0,
                1600L,
                800L,
                50.0,
                10000L,
                6000L,
                60.0));
        heartbeatStore.backendSamples.add(new BackendRuntimeMetricSample(
                NOW.minusSeconds(20),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                300L,
                400L,
                500L,
                7L,
                42));
        RuntimeManagementQueryService service = service(repository, heartbeatStore);

        RuntimeManagementBackendMetricHistory history = service.backendProcessMetrics(
                currentBackendId,
                Duration.ofMinutes(1),
                720,
                TRACE_ID);

        assertThat(heartbeatStore.lastServerLinuxServerId).isEqualTo(new LinuxServerId("10.8.0.12"));
        assertThat(history.samples()).hasSize(2);
        RuntimeManagementBackendMetricSample serverSample = history.samples().get(0);
        assertThat(serverSample.cpuUsagePercent()).isEqualTo(55.0);
        assertThat(serverSample.diskUsagePercent()).isEqualTo(60.0);
        assertThat(serverSample.jvmThreadsLive()).isNull();
        RuntimeManagementBackendMetricSample jvmSample = history.samples().get(1);
        assertThat(jvmSample.cpuUsagePercent()).isNull();
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
        assertThat(overview.managers()).containsExactly(manager());
        assertThat(overview.managerBackendConnections()).containsExactly(connection());
        assertThat(overview.opencodeProcesses().items()).extracting(row -> row.process().processId())
                .containsExactly(liveProcess.processId());
        assertThat(overview.summary().readyBackendProcesses()).isEqualTo(1);
        assertThat(overview.summary().connectedManagers()).isEqualTo(1);
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
        return new RuntimeManagementQueryService(
                repository,
                repository,
                heartbeatStore,
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
        @Override public List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit) { return List.of(); }
        @Override public OpencodeContainer saveContainer(OpencodeContainer container) { containers.put(container.containerId(), container); return container; }
        @Override public Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId) { return Optional.ofNullable(containers.get(containerId)); }
        @Override public List<OpencodeContainer> findHealthyContainers(int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackend(BackendProcessId backendProcessId, int limit) { return List.of(); }
        @Override public List<OpencodeContainer> findHealthyContainersConnectedToBackendByLinuxServer(BackendProcessId backendProcessId, LinuxServerId linuxServerId, int limit) { return List.of(); }
        @Override public OpencodeContainerManager saveContainerManager(OpencodeContainerManager manager) { managers.put(manager.managerId(), manager); return manager; }
        @Override public Optional<OpencodeContainerManager> findContainerManagerById(ContainerManagerId managerId) { return Optional.ofNullable(managers.get(managerId)); }
        @Override public OpencodeManagerBackendConnection saveManagerBackendConnection(OpencodeManagerBackendConnection connection) { connections.add(connection); return connection; }
        @Override public Optional<OpencodeManagerBackendConnection> findManagerBackendConnection(ContainerManagerId managerId, BackendProcessId backendProcessId) { return Optional.empty(); }
        @Override public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) { processes.add(process); return process; }
        @Override public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) { return processes.stream().filter(process -> process.processId().equals(processId)).findFirst(); }
        @Override public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) { return List.of(); }
        @Override public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) { bindings.put(binding.processId(), binding); return binding; }
        @Override public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) { return Optional.empty(); }
        @Override public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) { return processes.stream().limit(limit).toList(); }
        @Override public void save(User user) { users.put(user.userId(), user); }
        @Override public Optional<User> findByUserId(UserId userId) { return Optional.ofNullable(users.get(userId)); }
        @Override public Optional<User> findByUnifiedAuthId(String unifiedAuthId) { return users.values().stream().filter(user -> user.unifiedAuthId().equals(unifiedAuthId)).findFirst(); }
        @Override public Optional<User> findByUsername(String username) { return users.values().stream().filter(user -> user.username().equals(username)).findFirst(); }
        @Override public com.icbc.testagent.common.pagination.PageResponse<User> findPage(String keyword, PageRequest pageRequest) { return new PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0); }
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

    private static OpencodeProcessHeartbeatStore disabledHeartbeatStore() {
        return new OpencodeProcessHeartbeatStore() {
            @Override public void recordBackendHeartbeat(BackendProcessId backendProcessId, Instant heartbeatAt) { }
            @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) { }
            @Override public void recordManagerSnapshot(ManagerRuntimeSnapshot snapshot) { }
            @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { }
            @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
            @Override public List<ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
            @Override public Set<BackendProcessId> liveBackendProcessIds() { return Set.of(); }
            @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.of(); }
            @Override public void cleanupExpiredHeartbeats() { }
        };
    }

    private static final class RedisSnapshotHeartbeatStore implements OpencodeProcessHeartbeatStore {
        private final List<BackendRuntimeSnapshot> backendSnapshots = new java.util.ArrayList<>();
        private final List<ManagerRuntimeSnapshot> managerSnapshots = new java.util.ArrayList<>();
        private final List<ContainerRuntimeMetricSample> containerSamples = new java.util.ArrayList<>();
        private final List<BackendRuntimeMetricSample> backendSamples = new java.util.ArrayList<>();
        private final List<ServerRuntimeMetricSample> serverSamples = new java.util.ArrayList<>();
        private final Set<BackendProcessId> liveBackendProcessIds = new LinkedHashSet<>();
        private final Set<OpencodeProcessId> liveOpencodeProcessIds = new LinkedHashSet<>();
        private Instant lastContainerFrom;
        private Instant lastContainerTo;
        private LinuxServerId lastServerLinuxServerId;

        @Override public void recordBackendHeartbeat(BackendProcessId backendProcessId, Instant heartbeatAt) {
            liveBackendProcessIds.add(backendProcessId);
        }
        @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) {
            backendSnapshots.add(snapshot);
            liveBackendProcessIds.add(snapshot.backendProcess().backendProcessId());
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
        @Override public List<BackendRuntimeMetricSample> backendMetricSamples(BackendProcessId backendProcessId, Instant from, Instant to) {
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
        @Override public Set<BackendProcessId> liveBackendProcessIds() { return Set.copyOf(liveBackendProcessIds); }
        @Override public Set<OpencodeProcessId> liveOpencodeProcessIds() { return Set.copyOf(liveOpencodeProcessIds); }
        @Override public void cleanupExpiredHeartbeats() { }
    }
}
