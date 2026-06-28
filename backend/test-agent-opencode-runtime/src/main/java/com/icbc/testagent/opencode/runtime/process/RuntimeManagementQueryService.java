package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeMetricSample;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.ContainerRuntimeMetricSample;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.ManagedOpencodeProcessSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.ServerRuntimeMetricSample;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 超级管理员运行管理只读查询服务，聚合拓扑、连接和用户进程绑定快照。
 */
@Service
public class RuntimeManagementQueryService {

    private static final int TOPOLOGY_LIMIT = 500;
    private static final int PROCESS_SCAN_LIMIT = 200;

    private final OpencodeProcessManagementRepository repository;
    private final UserRepository userRepository;
    private final OpencodeProcessManagerGateway gateway;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final Clock clock;

    /**
     * 生产构造器使用 UTC 系统时钟。
     */
    @Autowired
    public RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        this(repository, userRepository, gateway, heartbeatStore, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟，避免快照时间不稳定。
     */
    public RuntimeManagementQueryService(OpencodeProcessManagementRepository repository, Clock clock) {
        this(repository, disabledUserRepository(), new UnavailableOpencodeProcessManagerGateway(), disabledHeartbeatStore(), clock);
    }

    /**
     * 测试构造器允许注入用户仓储，同时禁用 Redis 心跳依赖。
     */
    public RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            Clock clock) {
        this(repository, userRepository, new UnavailableOpencodeProcessManagerGateway(), disabledHeartbeatStore(), clock);
    }

    /**
     * 完整测试构造器允许替换时钟和心跳端口。
     */
    public RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock) {
        this(repository, userRepository, new UnavailableOpencodeProcessManagerGateway(), heartbeatStore, clock);
    }

    /**
     * 完整测试构造器允许替换时钟、心跳端口和 manager 网关。
     */
    public RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询管理页完整快照；traceId 预留给后续审计日志或指标，不影响只读结果。
     */
    public RuntimeManagementOverview overview(
            OpencodeServerProcessFilter filter,
            PageRequest pageRequest,
            String traceId) {
        Instant now = Instant.now(clock);
        Set<OpencodeProcessId> liveProcessIds = heartbeatStore.liveOpencodeProcessIds();
        List<BackendRuntimeSnapshot> backendSnapshots = heartbeatStore.liveBackendSnapshots().stream()
                .limit(TOPOLOGY_LIMIT)
                .toList();
        List<ManagerRuntimeSnapshot> managerSnapshots = heartbeatStore.liveManagerSnapshots().stream()
                .limit(TOPOLOGY_LIMIT)
                .toList();
        var linuxServersById = new LinkedHashMap<com.icbc.testagent.domain.opencodeprocess.LinuxServerId, com.icbc.testagent.domain.opencodeprocess.LinuxServer>();
        for (BackendRuntimeSnapshot snapshot : backendSnapshots) {
            linuxServersById.putIfAbsent(snapshot.linuxServer().linuxServerId(), snapshot.linuxServer());
        }
        var linuxServers = linuxServersById.values().stream().toList();
        var backendProcesses = backendSnapshots.stream()
                .map(snapshot -> new RuntimeManagementBackendProcess(snapshot.backendProcess(), snapshot.metrics()))
                .toList();
        var containers = managerSnapshots.stream()
                .map(snapshot -> new RuntimeManagementContainer(snapshot.container(), snapshot.metrics()))
                .toList();
        var managers = runtimeManagers(managerSnapshots);
        var connections = managerSnapshots.stream()
                .flatMap(snapshot -> snapshot.connections().stream())
                .limit(TOPOLOGY_LIMIT)
                .toList();
        Optional<OpencodeServerProcessFilter> resolvedFilter = resolveFilter(filter);
        PageResponse<OpencodeServerProcess> processPage = resolvedFilter
                .map(item -> liveProcessPage(item, pageRequest, liveProcessIds))
                .orElseGet(() -> new PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0));
        Map<OpencodeProcessId, UserOpencodeProcessBinding> bindings = repository.findUserBindingsByProcessIds(
                processPage.items().stream().map(OpencodeServerProcess::processId).toList());
        List<RuntimeManagementOpencodeProcess> rows = processPage.items().stream()
                .map(process -> new RuntimeManagementOpencodeProcess(
                        process,
                        Optional.ofNullable(bindings.get(process.processId())),
                        username(process.userId())))
                .toList();
        long runningOpencodeProcesses = processPage.total();

        RuntimeManagementSummary summary = new RuntimeManagementSummary(
                linuxServers.size(),
                linuxServers.size(),
                backendProcesses.size(),
                backendProcesses.size(),
                containers.size(),
                containers.size(),
                managers.size(),
                managers.size(),
                connections.size(),
                processPage.total(),
                runningOpencodeProcesses,
                repository.countUserBindings());

        return new RuntimeManagementOverview(
                Instant.now(clock),
                summary,
                linuxServers,
                backendProcesses,
                containers,
                managers,
                connections,
                new PageResponse<>(rows, processPage.page(), processPage.size(), processPage.total()));
    }

    /**
     * 按用户关键字查询 opencode server 进程，并通过 manager 主动探测 PID 与 HTTP 健康状态。
     */
    public PageResponse<RuntimeManagementOpencodeProcess> userProcesses(
            String keyword,
            PageRequest pageRequest,
            String traceId) {
        List<com.icbc.testagent.domain.user.User> users = resolveUsers(keyword);
        if (users.isEmpty()) {
            return new PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
        }
        Map<UserId, String> usernames = new LinkedHashMap<>();
        List<OpencodeServerProcess> processes = new ArrayList<>();
        for (com.icbc.testagent.domain.user.User user : users) {
            usernames.put(user.userId(), user.username());
            processes.addAll(repository.findOpencodeServerProcesses(
                    new OpencodeServerProcessFilter(null, null, null, user.userId()),
                    new PageRequest(1, PROCESS_SCAN_LIMIT)).items());
        }
        List<OpencodeServerProcess> ordered = processes.stream()
                .distinct()
                .sorted((left, right) -> right.updatedAt().compareTo(left.updatedAt()))
                .toList();
        List<OpencodeServerProcess> pageItems = ordered.stream()
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();
        Map<OpencodeProcessId, UserOpencodeProcessBinding> bindings = repository.findUserBindingsByProcessIds(
                pageItems.stream().map(OpencodeServerProcess::processId).toList());
        List<RuntimeManagementOpencodeProcess> rows = pageItems.stream()
                .map(process -> probeUserProcess(
                        process,
                        Optional.ofNullable(bindings.get(process.processId())),
                        Optional.ofNullable(usernames.get(process.userId())),
                        traceId))
                .toList();
        return new PageResponse<>(rows, pageRequest.page(), pageRequest.size(), ordered.size());
    }

    /**
     * 查询单个容器指定时间窗口内的运行指标历史，并按 maxPoints 做时间桶降采样。
     */
    public RuntimeManagementContainerMetricHistory containerMetrics(
            OpencodeContainerId containerId,
            Duration window,
            int maxPoints,
            String traceId) {
        Instant to = Instant.now(clock);
        Instant from = to.minus(window);
        List<ContainerRuntimeMetricSample> rawSamples = heartbeatStore.containerMetricSamples(containerId, from, to);
        return new RuntimeManagementContainerMetricHistory(
                to,
                containerId,
                from,
                to,
                downsampleContainerSamples(rawSamples, from, to, maxPoints));
    }

    /**
     * 查询单个后端 Java 进程指定时间窗口内的运行指标历史，并按 maxPoints 做时间桶降采样。
     */
    public RuntimeManagementBackendMetricHistory backendProcessMetrics(
            BackendProcessId backendProcessId,
            Duration window,
            int maxPoints,
            String traceId) {
        Instant to = Instant.now(clock);
        Instant from = to.minus(window);
        List<BackendRuntimeMetricSample> backendSamples = heartbeatStore.backendMetricSamples(backendProcessId, from, to);
        List<ServerRuntimeMetricSample> serverSamples = resolveBackendLinuxServerId(backendProcessId)
                .map(linuxServerId -> heartbeatStore.serverMetricSamples(linuxServerId, from, to))
                .orElse(List.of());
        List<BackendRuntimeMetricSample> rawSamples = mergeBackendMetricSamples(serverSamples, backendSamples);
        return new RuntimeManagementBackendMetricHistory(
                to,
                backendProcessId,
                from,
                to,
                downsampleBackendSamples(rawSamples, from, to, maxPoints));
    }

    private Optional<LinuxServerId> resolveBackendLinuxServerId(BackendProcessId backendProcessId) {
        for (BackendRuntimeSnapshot snapshot : heartbeatStore.liveBackendSnapshots()) {
            if (snapshot.backendProcess().backendProcessId().equals(backendProcessId)) {
                return Optional.of(snapshot.backendProcess().linuxServerId());
            }
        }
        return repository.findBackendJavaProcessById(backendProcessId)
                .map(process -> process.linuxServerId());
    }

    private List<BackendRuntimeMetricSample> mergeBackendMetricSamples(
            List<ServerRuntimeMetricSample> serverSamples,
            List<BackendRuntimeMetricSample> backendSamples) {
        if (serverSamples.isEmpty()) {
            return backendSamples;
        }
        Map<Instant, BackendMetricAccumulator> bySampledAt = new TreeMap<>();
        for (ServerRuntimeMetricSample sample : serverSamples) {
            BackendMetricAccumulator accumulator = bySampledAt.computeIfAbsent(sample.sampledAt(), BackendMetricAccumulator::new);
            accumulator.cpuUsagePercent = sample.cpuUsagePercent();
            accumulator.memoryMaxBytes = sample.memoryMaxBytes();
            accumulator.memoryUsedBytes = sample.memoryUsedBytes();
            accumulator.memoryUsagePercent = sample.memoryUsagePercent();
            accumulator.diskMaxBytes = sample.diskMaxBytes();
            accumulator.diskUsedBytes = sample.diskUsedBytes();
            accumulator.diskUsagePercent = sample.diskUsagePercent();
        }
        for (BackendRuntimeMetricSample sample : backendSamples) {
            BackendMetricAccumulator accumulator = bySampledAt.computeIfAbsent(sample.sampledAt(), BackendMetricAccumulator::new);
            // server:{linuxServerId} 是服务器级指标权威来源；backend:{backendProcessId} 只合并当前 JVM 指标。
            accumulator.jvmMemoryUsedBytes = sample.jvmMemoryUsedBytes();
            accumulator.jvmMemoryCommittedBytes = sample.jvmMemoryCommittedBytes();
            accumulator.jvmMemoryMaxBytes = sample.jvmMemoryMaxBytes();
            accumulator.jvmGcPauseMillis = sample.jvmGcPauseMillis();
            accumulator.jvmThreadsLive = sample.jvmThreadsLive();
        }
        return bySampledAt.values().stream()
                .map(BackendMetricAccumulator::toSample)
                .toList();
    }

    private PageResponse<OpencodeServerProcess> liveProcessPage(
            OpencodeServerProcessFilter filter,
            PageRequest pageRequest,
            Set<OpencodeProcessId> liveProcessIds) {
        if (filter.status() != null && filter.status() != OpencodeServerProcessStatus.RUNNING) {
            return new PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
        }
        OpencodeServerProcessFilter runningFilter = new OpencodeServerProcessFilter(
                OpencodeServerProcessStatus.RUNNING,
                filter.linuxServerId(),
                filter.containerId(),
                filter.userId());
        List<OpencodeServerProcess> liveProcesses = repository.findOpencodeServerProcesses(runningFilter, new PageRequest(1, PROCESS_SCAN_LIMIT))
                .items()
                .stream()
                .filter(process -> liveProcessIds.contains(process.processId()))
                .toList();
        List<OpencodeServerProcess> pageItems = liveProcesses.stream()
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();
        return new PageResponse<>(pageItems, pageRequest.page(), pageRequest.size(), liveProcesses.size());
    }

    private List<RuntimeManagementManager> runtimeManagers(List<ManagerRuntimeSnapshot> managerSnapshots) {
        Map<ManagedProcessKey, List<OpencodeServerProcess>> candidatesByKey = managedProcessCandidates(managerSnapshots);
        List<OpencodeProcessId> processIds = candidatesByKey.values().stream()
                .flatMap(List::stream)
                .map(OpencodeServerProcess::processId)
                .distinct()
                .toList();
        Map<OpencodeProcessId, UserOpencodeProcessBinding> bindings = processIds.isEmpty()
                ? Map.of()
                : repository.findUserBindingsByProcessIds(processIds);
        return managerSnapshots.stream()
                .map(snapshot -> new RuntimeManagementManager(
                        snapshot.manager(),
                        enrichManagedProcesses(snapshot, candidatesByKey, bindings)))
                .toList();
    }

    private Map<ManagedProcessKey, List<OpencodeServerProcess>> managedProcessCandidates(
            List<ManagerRuntimeSnapshot> managerSnapshots) {
        Map<ContainerKey, Set<Integer>> portsByContainer = new LinkedHashMap<>();
        for (ManagerRuntimeSnapshot snapshot : managerSnapshots) {
            ContainerKey key = new ContainerKey(
                    snapshot.manager().linuxServerId(),
                    snapshot.manager().containerId());
            for (ManagedOpencodeProcessSnapshot process : snapshot.managedProcesses()) {
                portsByContainer.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(process.port());
            }
        }
        Map<ManagedProcessKey, List<OpencodeServerProcess>> candidatesByKey = new LinkedHashMap<>();
        for (Map.Entry<ContainerKey, Set<Integer>> entry : portsByContainer.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            ContainerKey containerKey = entry.getKey();
            List<OpencodeServerProcess> candidates = repository.findOpencodeServerProcesses(
                            new OpencodeServerProcessFilter(
                                    null,
                                    containerKey.linuxServerId(),
                                    containerKey.containerId(),
                                    null),
                            new PageRequest(1, PROCESS_SCAN_LIMIT))
                    .items();
            for (OpencodeServerProcess candidate : candidates) {
                if (!entry.getValue().contains(candidate.port())) {
                    continue;
                }
                ManagedProcessKey key = new ManagedProcessKey(
                        candidate.linuxServerId(),
                        candidate.containerId(),
                        candidate.port());
                candidatesByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
            }
        }
        return candidatesByKey;
    }

    private List<RuntimeManagementManagedProcess> enrichManagedProcesses(
            ManagerRuntimeSnapshot snapshot,
            Map<ManagedProcessKey, List<OpencodeServerProcess>> candidatesByKey,
            Map<OpencodeProcessId, UserOpencodeProcessBinding> bindings) {
        List<RuntimeManagementManagedProcess> rows = new ArrayList<>();
        for (ManagedOpencodeProcessSnapshot managedProcess : snapshot.managedProcesses()) {
            ManagedProcessKey key = new ManagedProcessKey(
                    snapshot.manager().linuxServerId(),
                    snapshot.manager().containerId(),
                    managedProcess.port());
            List<OpencodeServerProcess> candidates = candidatesByKey.getOrDefault(key, List.of());
            Optional<OpencodeServerProcess> activeCandidate = candidates.stream()
                    .filter(candidate -> isActiveBinding(bindings.get(candidate.processId())))
                    .findFirst();
            if (activeCandidate.isPresent()) {
                OpencodeServerProcess process = activeCandidate.get();
                UserOpencodeProcessBinding binding = bindings.get(process.processId());
                rows.add(RuntimeManagementManagedProcess.bound(
                        managedProcess,
                        process,
                        binding,
                        username(binding.userId())));
                continue;
            }
            if (!candidates.isEmpty()) {
                rows.add(RuntimeManagementManagedProcess.unbound(managedProcess, candidates.getFirst()));
            } else {
                rows.add(RuntimeManagementManagedProcess.unbound(managedProcess));
            }
        }
        return rows;
    }

    private boolean isActiveBinding(UserOpencodeProcessBinding binding) {
        return binding != null && binding.status() == UserOpencodeProcessBindingStatus.ACTIVE;
    }

    private Optional<OpencodeServerProcessFilter> resolveFilter(OpencodeServerProcessFilter filter) {
        OpencodeServerProcessFilter source = filter == null ? OpencodeServerProcessFilter.empty() : filter;
        if (source.username() == null) {
            return Optional.of(source);
        }
        return userRepository.findByUsername(source.username())
                .map(user -> new OpencodeServerProcessFilter(
                        source.status(),
                        source.linuxServerId(),
                        source.containerId(),
                        user.userId()));
    }

    private List<com.icbc.testagent.domain.user.User> resolveUsers(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        Map<UserId, com.icbc.testagent.domain.user.User> users = new LinkedHashMap<>();
        try {
            userRepository.findByUserId(new UserId(normalized)).ifPresent(user -> users.put(user.userId(), user));
        } catch (IllegalArgumentException ignored) {
            // 关键字不是合法 userId 时继续按用户名和统一认证号查找。
        }
        userRepository.findByUsername(normalized).ifPresent(user -> users.put(user.userId(), user));
        userRepository.findByUnifiedAuthId(normalized).ifPresent(user -> users.put(user.userId(), user));
        userRepository.findPage(normalized, new PageRequest(1, 50))
                .items()
                .forEach(user -> users.put(user.userId(), user));
        return users.values().stream().toList();
    }

    private RuntimeManagementOpencodeProcess probeUserProcess(
            OpencodeServerProcess process,
            Optional<UserOpencodeProcessBinding> binding,
            Optional<String> username,
            String traceId) {
        Instant checkedAt = Instant.now(clock);
        try {
            OpencodeProcessHealthResult health = gateway.checkHealth(new OpencodeProcessHealthCommand(
                    process.processId(),
                    process.baseUrl(),
                    traceId));
            if (health.healthy()) {
                OpencodeServerProcess running = updateProcessProbeSnapshot(
                        process,
                        OpencodeServerProcessStatus.RUNNING,
                        checkedAt,
                        health.message(),
                        traceId);
                heartbeatStore.recordOpencodeHeartbeat(running.processId(), checkedAt);
                return new RuntimeManagementOpencodeProcess(
                        running,
                        binding,
                        username,
                        "RUNNING",
                        "HEALTHY",
                        false);
            }
            OpencodeServerProcessStatus status = notRunningMessage(health.message())
                    ? OpencodeServerProcessStatus.STOPPED
                    : OpencodeServerProcessStatus.UNHEALTHY;
            OpencodeServerProcess unhealthy = updateProcessProbeSnapshot(
                    process,
                    status,
                    checkedAt,
                    health.message(),
                    traceId);
            String probeStatus = status == OpencodeServerProcessStatus.STOPPED ? "NOT_RUNNING" : "UNHEALTHY";
            return new RuntimeManagementOpencodeProcess(unhealthy, binding, username, probeStatus, probeStatus, true);
        } catch (RuntimeException exception) {
            OpencodeServerProcess failed = updateProcessProbeSnapshot(
                    process,
                    OpencodeServerProcessStatus.FAILED,
                    checkedAt,
                    exception.getMessage(),
                    traceId);
            return new RuntimeManagementOpencodeProcess(failed, binding, username, "CHECK_FAILED", "CHECK_FAILED", true);
        }
    }

    private OpencodeServerProcess updateProcessProbeSnapshot(
            OpencodeServerProcess process,
            OpencodeServerProcessStatus status,
            Instant checkedAt,
            String healthMessage,
            String traceId) {
        OpencodeServerProcess updated = new OpencodeServerProcess(
                process.processId(),
                process.userId(),
                process.linuxServerId(),
                process.containerId(),
                process.port(),
                process.pid(),
                process.baseUrl(),
                status,
                process.sessionPath(),
                process.configPath(),
                process.startedAt(),
                checkedAt,
                healthMessage,
                process.createdAt(),
                checkedAt,
                traceId);
        return repository.saveOpencodeServerProcess(updated);
    }

    private boolean notRunningMessage(String message) {
        String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("pid is not alive")
                || normalized.contains("process is not running")
                || normalized.contains("process not found")
                || normalized.contains("state not found")
                || normalized.contains("already stopped");
    }

    private Optional<String> username(UserId userId) {
        return userRepository.findByUserId(userId).map(user -> user.username());
    }

    private List<RuntimeManagementContainerMetricSample> downsampleContainerSamples(
            List<ContainerRuntimeMetricSample> samples,
            Instant from,
            Instant to,
            int maxPoints) {
        if (samples.size() <= maxPoints) {
            return samples.stream().map(this::containerMetricSample).toList();
        }
        List<List<ContainerRuntimeMetricSample>> buckets = containerBuckets(samples, from, to, maxPoints);
        List<RuntimeManagementContainerMetricSample> result = new ArrayList<>();
        for (List<ContainerRuntimeMetricSample> bucket : buckets) {
            if (bucket.isEmpty()) {
                continue;
            }
            ContainerRuntimeMetricSample last = bucket.get(bucket.size() - 1);
            result.add(new RuntimeManagementContainerMetricSample(
                    last.sampledAt(),
                    last.maxProcesses(),
                    last.currentProcesses(),
                    last.metricsSource(),
                    averageDouble(bucket.stream().map(ContainerRuntimeMetricSample::cpuUsagePercent).toList()),
                    averageLong(bucket.stream().map(ContainerRuntimeMetricSample::memoryMaxBytes).toList()),
                    averageLong(bucket.stream().map(ContainerRuntimeMetricSample::memoryUsedBytes).toList()),
                    averageDouble(bucket.stream().map(ContainerRuntimeMetricSample::memoryUsagePercent).toList()),
                    averageDouble(bucket.stream().map(ContainerRuntimeMetricSample::diskReadBytesPerSecond).toList()),
                    averageDouble(bucket.stream().map(ContainerRuntimeMetricSample::diskWriteBytesPerSecond).toList())));
        }
        return result;
    }

    private List<RuntimeManagementBackendMetricSample> downsampleBackendSamples(
            List<BackendRuntimeMetricSample> samples,
            Instant from,
            Instant to,
            int maxPoints) {
        if (samples.size() <= maxPoints) {
            return samples.stream().map(this::backendMetricSample).toList();
        }
        List<List<BackendRuntimeMetricSample>> buckets = backendBuckets(samples, from, to, maxPoints);
        List<RuntimeManagementBackendMetricSample> result = new ArrayList<>();
        for (List<BackendRuntimeMetricSample> bucket : buckets) {
            if (bucket.isEmpty()) {
                continue;
            }
            BackendRuntimeMetricSample last = bucket.get(bucket.size() - 1);
            result.add(new RuntimeManagementBackendMetricSample(
                    last.sampledAt(),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::cpuUsagePercent).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::memoryMaxBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::memoryUsedBytes).toList()),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::memoryUsagePercent).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::diskMaxBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::diskUsedBytes).toList()),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::diskUsagePercent).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmMemoryUsedBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmMemoryCommittedBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmMemoryMaxBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmGcPauseMillis).toList()),
                    averageInteger(bucket.stream().map(BackendRuntimeMetricSample::jvmThreadsLive).toList())));
        }
        return result;
    }

    private List<List<ContainerRuntimeMetricSample>> containerBuckets(
            List<ContainerRuntimeMetricSample> samples,
            Instant from,
            Instant to,
            int maxPoints) {
        List<List<ContainerRuntimeMetricSample>> buckets = emptyBuckets(maxPoints);
        long bucketMillis = bucketMillis(from, to, maxPoints);
        for (ContainerRuntimeMetricSample sample : samples) {
            buckets.get(bucketIndex(sample.sampledAt(), from, bucketMillis, maxPoints)).add(sample);
        }
        return buckets;
    }

    private List<List<BackendRuntimeMetricSample>> backendBuckets(
            List<BackendRuntimeMetricSample> samples,
            Instant from,
            Instant to,
            int maxPoints) {
        List<List<BackendRuntimeMetricSample>> buckets = emptyBuckets(maxPoints);
        long bucketMillis = bucketMillis(from, to, maxPoints);
        for (BackendRuntimeMetricSample sample : samples) {
            buckets.get(bucketIndex(sample.sampledAt(), from, bucketMillis, maxPoints)).add(sample);
        }
        return buckets;
    }

    private <T> List<List<T>> emptyBuckets(int maxPoints) {
        List<List<T>> buckets = new ArrayList<>(maxPoints);
        for (int index = 0; index < maxPoints; index++) {
            buckets.add(new ArrayList<>());
        }
        return buckets;
    }

    private long bucketMillis(Instant from, Instant to, int maxPoints) {
        long spanMillis = Math.max(1, Duration.between(from, to).toMillis());
        return Math.max(1, (long) Math.ceil((double) spanMillis / maxPoints));
    }

    private int bucketIndex(Instant sampledAt, Instant from, long bucketMillis, int maxPoints) {
        long offset = Math.max(0, Duration.between(from, sampledAt).toMillis());
        return (int) Math.min(maxPoints - 1L, offset / bucketMillis);
    }

    private RuntimeManagementContainerMetricSample containerMetricSample(ContainerRuntimeMetricSample sample) {
        return new RuntimeManagementContainerMetricSample(
                sample.sampledAt(),
                sample.maxProcesses(),
                sample.currentProcesses(),
                sample.metricsSource(),
                sample.cpuUsagePercent(),
                sample.memoryMaxBytes(),
                sample.memoryUsedBytes(),
                sample.memoryUsagePercent(),
                sample.diskReadBytesPerSecond(),
                sample.diskWriteBytesPerSecond());
    }

    private RuntimeManagementBackendMetricSample backendMetricSample(BackendRuntimeMetricSample sample) {
        return new RuntimeManagementBackendMetricSample(
                sample.sampledAt(),
                sample.cpuUsagePercent(),
                sample.memoryMaxBytes(),
                sample.memoryUsedBytes(),
                sample.memoryUsagePercent(),
                sample.diskMaxBytes(),
                sample.diskUsedBytes(),
                sample.diskUsagePercent(),
                sample.jvmMemoryUsedBytes(),
                sample.jvmMemoryCommittedBytes(),
                sample.jvmMemoryMaxBytes(),
                sample.jvmGcPauseMillis(),
                sample.jvmThreadsLive());
    }

    private Double averageDouble(List<Double> values) {
        List<Double> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        return present.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private Long averageLong(List<Long> values) {
        List<Long> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        return Math.round(present.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private Integer averageInteger(List<Integer> values) {
        List<Integer> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        return (int) Math.round(present.stream().mapToInt(Integer::intValue).average().orElse(0));
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

    private static UserRepository disabledUserRepository() {
        return new UserRepository() {
            @Override public void save(com.icbc.testagent.domain.user.User user) { }
            @Override public Optional<com.icbc.testagent.domain.user.User> findByUserId(UserId userId) { return Optional.empty(); }
            @Override public Optional<com.icbc.testagent.domain.user.User> findByUnifiedAuthId(String unifiedAuthId) { return Optional.empty(); }
            @Override public Optional<com.icbc.testagent.domain.user.User> findByUsername(String username) { return Optional.empty(); }
            @Override public PageResponse<com.icbc.testagent.domain.user.User> findPage(String keyword, PageRequest pageRequest) {
                return new PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
            }
            @Override public boolean existsByUsername(String username) { return false; }
            @Override public boolean existsByUnifiedAuthId(String unifiedAuthId) { return false; }
        };
    }

    private record ContainerKey(LinuxServerId linuxServerId, OpencodeContainerId containerId) {
    }

    private record ManagedProcessKey(LinuxServerId linuxServerId, OpencodeContainerId containerId, int port) {
    }

    private static final class BackendMetricAccumulator {
        private final Instant sampledAt;
        private Double cpuUsagePercent;
        private Long memoryMaxBytes;
        private Long memoryUsedBytes;
        private Double memoryUsagePercent;
        private Long diskMaxBytes;
        private Long diskUsedBytes;
        private Double diskUsagePercent;
        private Long jvmMemoryUsedBytes;
        private Long jvmMemoryCommittedBytes;
        private Long jvmMemoryMaxBytes;
        private Long jvmGcPauseMillis;
        private Integer jvmThreadsLive;

        private BackendMetricAccumulator(Instant sampledAt) {
            this.sampledAt = sampledAt;
        }

        private BackendRuntimeMetricSample toSample() {
            return new BackendRuntimeMetricSample(
                    sampledAt,
                    cpuUsagePercent,
                    memoryMaxBytes,
                    memoryUsedBytes,
                    memoryUsagePercent,
                    diskMaxBytes,
                    diskUsedBytes,
                    diskUsagePercent,
                    jvmMemoryUsedBytes,
                    jvmMemoryCommittedBytes,
                    jvmMemoryMaxBytes,
                    jvmGcPauseMillis,
                    jvmThreadsLive);
        }
    }
}
