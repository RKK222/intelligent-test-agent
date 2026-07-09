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
import java.util.Comparator;
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
    private final BackendJavaRouteResolver routeResolver;
    private final OpencodeProcessStatusQueryService statusQueryService;
    private final Clock clock;

    /**
     * 生产构造器使用 UTC 系统时钟。
     */
    @Autowired
    public RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            BackendJavaRouteResolver routeResolver,
            OpencodeProcessStatusQueryService statusQueryService) {
        this(repository, userRepository, gateway, heartbeatStore, routeResolver, statusQueryService, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟，避免快照时间不稳定。
     */
    public RuntimeManagementQueryService(OpencodeProcessManagementRepository repository, Clock clock) {
        this(repository, disabledUserRepository(), new UnavailableOpencodeProcessManagerGateway(), disabledHeartbeatStore(), null, clock);
    }

    /**
     * 测试构造器允许注入用户仓储，同时禁用 Redis 心跳依赖。
     */
    public RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            Clock clock) {
        this(repository, userRepository, new UnavailableOpencodeProcessManagerGateway(), disabledHeartbeatStore(), null, clock);
    }

    /**
     * 完整测试构造器允许替换时钟和心跳端口。
     */
    public RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock) {
        this(repository, userRepository, new UnavailableOpencodeProcessManagerGateway(), heartbeatStore, null, clock);
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
        this(repository, userRepository, gateway, heartbeatStore, null, clock);
    }

    /**
     * 完整测试构造器允许替换时钟、心跳端口、manager 网关和统一路由解析器。
     */
    public RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            BackendJavaRouteResolver routeResolver,
            Clock clock) {
        this(repository, userRepository, gateway, heartbeatStore, routeResolver, null, clock);
    }

    /**
     * 完整测试构造器允许替换时钟、manager 网关、统一路由解析器和公共状态查询服务。
     */
    RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            BackendJavaRouteResolver routeResolver,
            OpencodeProcessStatusQueryService statusQueryService,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.routeResolver = routeResolver;
        this.statusQueryService = statusQueryService == null
                ? new OpencodeProcessStatusQueryService(repository, gateway, heartbeatStore, clock)
                : statusQueryService;
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
     * 按稳定服务器身份查询 Java 服务指定时间窗口内的运行指标历史，并按 maxPoints 做时间桶降采样。
     */
    public RuntimeManagementBackendMetricHistory backendServerMetrics(
            LinuxServerId linuxServerId,
            Duration window,
            int maxPoints,
            String traceId) {
        Instant to = Instant.now(clock);
        Instant from = to.minus(window);
        List<BackendRuntimeMetricSample> backendSamples = heartbeatStore.backendMetricSamples(linuxServerId, from, to);
        List<ServerRuntimeMetricSample> serverSamples = heartbeatStore.serverMetricSamples(linuxServerId, from, to);
        List<BackendRuntimeMetricSample> rawSamples = mergeBackendMetricSamples(serverSamples, backendSamples);
        return new RuntimeManagementBackendMetricHistory(
                to,
                linuxServerId,
                resolveLiveBackendProcessId(linuxServerId),
                from,
                to,
                downsampleBackendSamples(rawSamples, from, to, maxPoints));
    }

    /**
     * 兼容旧 backendProcessId history API：优先解析到稳定服务器身份后复用新主路径，解析失败时读取旧 key。
     */
    public RuntimeManagementBackendMetricHistory backendProcessMetrics(
            BackendProcessId backendProcessId,
            Duration window,
            int maxPoints,
            String traceId) {
        Optional<LinuxServerId> linuxServerId = resolveBackendLinuxServerId(backendProcessId);
        if (linuxServerId.isPresent()) {
            RuntimeManagementBackendMetricHistory history = backendServerMetrics(linuxServerId.get(), window, maxPoints, traceId);
            return new RuntimeManagementBackendMetricHistory(
                    history.generatedAt(),
                    history.linuxServerId(),
                    Optional.of(backendProcessId),
                    history.from(),
                    history.to(),
                    history.samples());
        }
        Instant to = Instant.now(clock);
        Instant from = to.minus(window);
        List<BackendRuntimeMetricSample> rawSamples = heartbeatStore.legacyBackendMetricSamples(backendProcessId, from, to);
        return new RuntimeManagementBackendMetricHistory(
                to,
                null,
                Optional.of(backendProcessId),
                from,
                to,
                downsampleBackendSamples(rawSamples, from, to, maxPoints));
    }

    private Optional<BackendProcessId> resolveLiveBackendProcessId(LinuxServerId linuxServerId) {
        return heartbeatStore.liveBackendSnapshots().stream()
                .filter(snapshot -> snapshot.backendProcess().linuxServerId().equals(linuxServerId))
                .max(Comparator.comparing(snapshot -> snapshot.backendProcess().lastHeartbeatAt()))
                .map(snapshot -> snapshot.backendProcess().backendProcessId());
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
            accumulator.cpuCoreCount = sample.cpuCoreCount();
            accumulator.loadAverage1m = sample.loadAverage1m();
            accumulator.loadAverage5m = sample.loadAverage5m();
            accumulator.loadAverage15m = sample.loadAverage15m();
            accumulator.memoryMaxBytes = sample.memoryMaxBytes();
            accumulator.memoryTotalBytes = sample.memoryTotalBytes();
            accumulator.memoryAvailableBytes = sample.memoryAvailableBytes();
            accumulator.memoryFreeBytes = sample.memoryFreeBytes();
            accumulator.memoryUsedBytes = sample.memoryUsedBytes();
            accumulator.memoryUsagePercent = sample.memoryUsagePercent();
            accumulator.memoryBuffersBytes = sample.memoryBuffersBytes();
            accumulator.memoryCachedBytes = sample.memoryCachedBytes();
            accumulator.swapTotalBytes = sample.swapTotalBytes();
            accumulator.swapFreeBytes = sample.swapFreeBytes();
            accumulator.swapUsedBytes = sample.swapUsedBytes();
            accumulator.swapUsagePercent = sample.swapUsagePercent();
            accumulator.diskMaxBytes = sample.diskMaxBytes();
            accumulator.diskAvailableBytes = sample.diskAvailableBytes();
            accumulator.diskUsedBytes = sample.diskUsedBytes();
            accumulator.diskUsagePercent = sample.diskUsagePercent();
        }
        for (BackendRuntimeMetricSample sample : backendSamples) {
            BackendMetricAccumulator accumulator = bySampledAt.computeIfAbsent(sample.sampledAt(), BackendMetricAccumulator::new);
            // server:{linuxServerId} 是服务器级指标权威来源；backend:{backendProcessId} 只合并当前 JVM 指标。
            accumulator.jvmProcessCpuUsagePercent = sample.jvmProcessCpuUsagePercent();
            accumulator.jvmProcessCpuCoreUsage = sample.jvmProcessCpuCoreUsage();
            accumulator.jvmProcessCpuTimeNanos = sample.jvmProcessCpuTimeNanos();
            accumulator.jvmProcessResidentMemoryBytes = sample.jvmProcessResidentMemoryBytes();
            accumulator.jvmProcessPeakResidentMemoryBytes = sample.jvmProcessPeakResidentMemoryBytes();
            accumulator.jvmProcessVirtualMemoryBytes = sample.jvmProcessVirtualMemoryBytes();
            accumulator.jvmProcessSwapBytes = sample.jvmProcessSwapBytes();
            accumulator.jvmOpenFileDescriptorCount = sample.jvmOpenFileDescriptorCount();
            accumulator.jvmMaxFileDescriptorCount = sample.jvmMaxFileDescriptorCount();
            accumulator.jvmMemoryUsedBytes = sample.jvmMemoryUsedBytes();
            accumulator.jvmMemoryCommittedBytes = sample.jvmMemoryCommittedBytes();
            accumulator.jvmMemoryMaxBytes = sample.jvmMemoryMaxBytes();
            accumulator.jvmHeapUsedBytes = sample.jvmHeapUsedBytes();
            accumulator.jvmHeapCommittedBytes = sample.jvmHeapCommittedBytes();
            accumulator.jvmHeapMaxBytes = sample.jvmHeapMaxBytes();
            accumulator.jvmNonHeapUsedBytes = sample.jvmNonHeapUsedBytes();
            accumulator.jvmNonHeapCommittedBytes = sample.jvmNonHeapCommittedBytes();
            accumulator.jvmNonHeapMaxBytes = sample.jvmNonHeapMaxBytes();
            accumulator.jvmDirectBufferCount = sample.jvmDirectBufferCount();
            accumulator.jvmDirectBufferUsedBytes = sample.jvmDirectBufferUsedBytes();
            accumulator.jvmDirectBufferCapacityBytes = sample.jvmDirectBufferCapacityBytes();
            accumulator.jvmMappedBufferCount = sample.jvmMappedBufferCount();
            accumulator.jvmMappedBufferUsedBytes = sample.jvmMappedBufferUsedBytes();
            accumulator.jvmMappedBufferCapacityBytes = sample.jvmMappedBufferCapacityBytes();
            accumulator.jvmGcPauseMillis = sample.jvmGcPauseMillis();
            accumulator.jvmGcCollectionTimeDeltaMillis = sample.jvmGcCollectionTimeDeltaMillis();
            accumulator.jvmGcCollectionCountDelta = sample.jvmGcCollectionCountDelta();
            accumulator.jvmGcTimePercent = sample.jvmGcTimePercent();
            accumulator.jvmThreadsLive = sample.jvmThreadsLive();
            accumulator.jvmThreadsDaemon = sample.jvmThreadsDaemon();
            accumulator.jvmThreadsPeak = sample.jvmThreadsPeak();
            accumulator.jvmThreadsTotalStarted = sample.jvmThreadsTotalStarted();
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
        if (routeResolver != null && !routeResolver.isCurrent(process.linuxServerId())) {
            return new RuntimeManagementOpencodeProcess(
                    process,
                    binding,
                    username,
                    "REMOTE_SERVER",
                    "CHECK_SKIPPED",
                    true);
        }
        OpencodeProcessStatusProbe probe = statusQueryService.query(process.processId(), traceId);
        return new RuntimeManagementOpencodeProcess(
                probe.process().orElse(process),
                binding,
                username,
                probe.managerStatus(),
                probe.healthStatus(),
                probe.restartable());
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
                    averageInteger(bucket.stream().map(BackendRuntimeMetricSample::cpuCoreCount).toList()),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::loadAverage1m).toList()),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::loadAverage5m).toList()),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::loadAverage15m).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::memoryMaxBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::memoryTotalBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::memoryAvailableBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::memoryFreeBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::memoryUsedBytes).toList()),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::memoryUsagePercent).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::memoryBuffersBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::memoryCachedBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::swapTotalBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::swapFreeBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::swapUsedBytes).toList()),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::swapUsagePercent).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::diskMaxBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::diskAvailableBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::diskUsedBytes).toList()),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::diskUsagePercent).toList()),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::jvmProcessCpuUsagePercent).toList()),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::jvmProcessCpuCoreUsage).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmProcessCpuTimeNanos).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmProcessResidentMemoryBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmProcessPeakResidentMemoryBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmProcessVirtualMemoryBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmProcessSwapBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmOpenFileDescriptorCount).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmMaxFileDescriptorCount).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmMemoryUsedBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmMemoryCommittedBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmMemoryMaxBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmHeapUsedBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmHeapCommittedBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmHeapMaxBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmNonHeapUsedBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmNonHeapCommittedBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmNonHeapMaxBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmDirectBufferCount).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmDirectBufferUsedBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmDirectBufferCapacityBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmMappedBufferCount).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmMappedBufferUsedBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmMappedBufferCapacityBytes).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmGcPauseMillis).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmGcCollectionTimeDeltaMillis).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmGcCollectionCountDelta).toList()),
                    averageDouble(bucket.stream().map(BackendRuntimeMetricSample::jvmGcTimePercent).toList()),
                    averageInteger(bucket.stream().map(BackendRuntimeMetricSample::jvmThreadsLive).toList()),
                    averageInteger(bucket.stream().map(BackendRuntimeMetricSample::jvmThreadsDaemon).toList()),
                    averageInteger(bucket.stream().map(BackendRuntimeMetricSample::jvmThreadsPeak).toList()),
                    averageLong(bucket.stream().map(BackendRuntimeMetricSample::jvmThreadsTotalStarted).toList())));
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
                sample.cpuCoreCount(),
                sample.loadAverage1m(),
                sample.loadAverage5m(),
                sample.loadAverage15m(),
                sample.memoryMaxBytes(),
                sample.memoryTotalBytes(),
                sample.memoryAvailableBytes(),
                sample.memoryFreeBytes(),
                sample.memoryUsedBytes(),
                sample.memoryUsagePercent(),
                sample.memoryBuffersBytes(),
                sample.memoryCachedBytes(),
                sample.swapTotalBytes(),
                sample.swapFreeBytes(),
                sample.swapUsedBytes(),
                sample.swapUsagePercent(),
                sample.diskMaxBytes(),
                sample.diskAvailableBytes(),
                sample.diskUsedBytes(),
                sample.diskUsagePercent(),
                sample.jvmProcessCpuUsagePercent(),
                sample.jvmProcessCpuCoreUsage(),
                sample.jvmProcessCpuTimeNanos(),
                sample.jvmProcessResidentMemoryBytes(),
                sample.jvmProcessPeakResidentMemoryBytes(),
                sample.jvmProcessVirtualMemoryBytes(),
                sample.jvmProcessSwapBytes(),
                sample.jvmOpenFileDescriptorCount(),
                sample.jvmMaxFileDescriptorCount(),
                sample.jvmMemoryUsedBytes(),
                sample.jvmMemoryCommittedBytes(),
                sample.jvmMemoryMaxBytes(),
                sample.jvmHeapUsedBytes(),
                sample.jvmHeapCommittedBytes(),
                sample.jvmHeapMaxBytes(),
                sample.jvmNonHeapUsedBytes(),
                sample.jvmNonHeapCommittedBytes(),
                sample.jvmNonHeapMaxBytes(),
                sample.jvmDirectBufferCount(),
                sample.jvmDirectBufferUsedBytes(),
                sample.jvmDirectBufferCapacityBytes(),
                sample.jvmMappedBufferCount(),
                sample.jvmMappedBufferUsedBytes(),
                sample.jvmMappedBufferCapacityBytes(),
                sample.jvmGcPauseMillis(),
                sample.jvmGcCollectionTimeDeltaMillis(),
                sample.jvmGcCollectionCountDelta(),
                sample.jvmGcTimePercent(),
                sample.jvmThreadsLive(),
                sample.jvmThreadsDaemon(),
                sample.jvmThreadsPeak(),
                sample.jvmThreadsTotalStarted());
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
        private Integer cpuCoreCount;
        private Double loadAverage1m;
        private Double loadAverage5m;
        private Double loadAverage15m;
        private Long memoryMaxBytes;
        private Long memoryTotalBytes;
        private Long memoryAvailableBytes;
        private Long memoryFreeBytes;
        private Long memoryUsedBytes;
        private Double memoryUsagePercent;
        private Long memoryBuffersBytes;
        private Long memoryCachedBytes;
        private Long swapTotalBytes;
        private Long swapFreeBytes;
        private Long swapUsedBytes;
        private Double swapUsagePercent;
        private Long diskMaxBytes;
        private Long diskAvailableBytes;
        private Long diskUsedBytes;
        private Double diskUsagePercent;
        private Double jvmProcessCpuUsagePercent;
        private Double jvmProcessCpuCoreUsage;
        private Long jvmProcessCpuTimeNanos;
        private Long jvmProcessResidentMemoryBytes;
        private Long jvmProcessPeakResidentMemoryBytes;
        private Long jvmProcessVirtualMemoryBytes;
        private Long jvmProcessSwapBytes;
        private Long jvmOpenFileDescriptorCount;
        private Long jvmMaxFileDescriptorCount;
        private Long jvmMemoryUsedBytes;
        private Long jvmMemoryCommittedBytes;
        private Long jvmMemoryMaxBytes;
        private Long jvmHeapUsedBytes;
        private Long jvmHeapCommittedBytes;
        private Long jvmHeapMaxBytes;
        private Long jvmNonHeapUsedBytes;
        private Long jvmNonHeapCommittedBytes;
        private Long jvmNonHeapMaxBytes;
        private Long jvmDirectBufferCount;
        private Long jvmDirectBufferUsedBytes;
        private Long jvmDirectBufferCapacityBytes;
        private Long jvmMappedBufferCount;
        private Long jvmMappedBufferUsedBytes;
        private Long jvmMappedBufferCapacityBytes;
        private Long jvmGcPauseMillis;
        private Long jvmGcCollectionTimeDeltaMillis;
        private Long jvmGcCollectionCountDelta;
        private Double jvmGcTimePercent;
        private Integer jvmThreadsLive;
        private Integer jvmThreadsDaemon;
        private Integer jvmThreadsPeak;
        private Long jvmThreadsTotalStarted;

        private BackendMetricAccumulator(Instant sampledAt) {
            this.sampledAt = sampledAt;
        }

        private BackendRuntimeMetricSample toSample() {
            return new BackendRuntimeMetricSample(
                    sampledAt,
                    cpuUsagePercent,
                    cpuCoreCount,
                    loadAverage1m,
                    loadAverage5m,
                    loadAverage15m,
                    memoryMaxBytes,
                    memoryTotalBytes,
                    memoryAvailableBytes,
                    memoryFreeBytes,
                    memoryUsedBytes,
                    memoryUsagePercent,
                    memoryBuffersBytes,
                    memoryCachedBytes,
                    swapTotalBytes,
                    swapFreeBytes,
                    swapUsedBytes,
                    swapUsagePercent,
                    diskMaxBytes,
                    diskAvailableBytes,
                    diskUsedBytes,
                    diskUsagePercent,
                    jvmProcessCpuUsagePercent,
                    jvmProcessCpuCoreUsage,
                    jvmProcessCpuTimeNanos,
                    jvmProcessResidentMemoryBytes,
                    jvmProcessPeakResidentMemoryBytes,
                    jvmProcessVirtualMemoryBytes,
                    jvmProcessSwapBytes,
                    jvmOpenFileDescriptorCount,
                    jvmMaxFileDescriptorCount,
                    jvmMemoryUsedBytes,
                    jvmMemoryCommittedBytes,
                    jvmMemoryMaxBytes,
                    jvmHeapUsedBytes,
                    jvmHeapCommittedBytes,
                    jvmHeapMaxBytes,
                    jvmNonHeapUsedBytes,
                    jvmNonHeapCommittedBytes,
                    jvmNonHeapMaxBytes,
                    jvmDirectBufferCount,
                    jvmDirectBufferUsedBytes,
                    jvmDirectBufferCapacityBytes,
                    jvmMappedBufferCount,
                    jvmMappedBufferUsedBytes,
                    jvmMappedBufferCapacityBytes,
                    jvmGcPauseMillis,
                    jvmGcCollectionTimeDeltaMillis,
                    jvmGcCollectionCountDelta,
                    jvmGcTimePercent,
                    jvmThreadsLive,
                    jvmThreadsDaemon,
                    jvmThreadsPeak,
                    jvmThreadsTotalStarted);
        }
    }
}
