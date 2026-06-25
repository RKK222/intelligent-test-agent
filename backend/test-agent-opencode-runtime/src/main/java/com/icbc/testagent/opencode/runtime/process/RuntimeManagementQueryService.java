package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 超级管理员运行管理只读查询服务，聚合拓扑、连接和用户进程绑定快照。
 */
@Service
public class RuntimeManagementQueryService {

    private static final int TOPOLOGY_LIMIT = 500;
    private static final int PROCESS_SCAN_LIMIT = 200;
    private static final Duration LIVE_WINDOW = Duration.ofMinutes(5);

    private final OpencodeProcessManagementRepository repository;
    private final UserRepository userRepository;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final Clock clock;

    /**
     * 生产构造器使用 UTC 系统时钟。
     */
    @Autowired
    public RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        this(repository, userRepository, heartbeatStore, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟，避免快照时间不稳定。
     */
    public RuntimeManagementQueryService(OpencodeProcessManagementRepository repository, Clock clock) {
        this(repository, disabledUserRepository(), disabledHeartbeatStore(), clock);
    }

    /**
     * 测试构造器允许注入用户仓储，同时禁用 Redis 心跳依赖。
     */
    public RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            Clock clock) {
        this(repository, userRepository, disabledHeartbeatStore(), clock);
    }

    /**
     * 完整测试构造器允许替换时钟和心跳端口。
     */
    public RuntimeManagementQueryService(
            OpencodeProcessManagementRepository repository,
            UserRepository userRepository,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
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
        Instant minLiveAt = now.minus(LIVE_WINDOW);
        Set<BackendProcessId> liveBackendIds = heartbeatStore.enabled() ? heartbeatStore.liveBackendProcessIds() : Collections.emptySet();
        Set<OpencodeProcessId> liveProcessIds = heartbeatStore.enabled() ? heartbeatStore.liveOpencodeProcessIds() : Collections.emptySet();
        var linuxServers = repository.findLinuxServers(TOPOLOGY_LIMIT).stream()
                .filter(server -> server.status() == LinuxServerStatus.READY)
                .filter(server -> !server.lastHeartbeatAt().isBefore(minLiveAt))
                .toList();
        var backendProcesses = repository.findBackendJavaProcesses(TOPOLOGY_LIMIT).stream()
                .filter(process -> process.status() == BackendJavaProcessStatus.READY)
                .filter(process -> heartbeatStore.enabled()
                        ? liveBackendIds.contains(process.backendProcessId())
                        : !process.lastHeartbeatAt().isBefore(minLiveAt))
                .toList();
        var containers = repository.findContainers(TOPOLOGY_LIMIT).stream()
                .filter(container -> container.status() == OpencodeContainerStatus.READY)
                .filter(container -> !container.lastHeartbeatAt().isBefore(minLiveAt))
                .toList();
        var managers = repository.findContainerManagers(TOPOLOGY_LIMIT).stream()
                .filter(manager -> manager.connectionStatus() == ManagerConnectionStatus.CONNECTED)
                .filter(manager -> !manager.lastHeartbeatAt().isBefore(minLiveAt))
                .toList();
        var connections = repository.findManagerBackendConnections(TOPOLOGY_LIMIT).stream()
                .filter(connection -> connection.status() == ManagerConnectionStatus.CONNECTED)
                .filter(connection -> !connection.lastHeartbeatAt().isBefore(minLiveAt))
                .toList();
        Optional<OpencodeServerProcessFilter> resolvedFilter = resolveFilter(filter);
        PageResponse<OpencodeServerProcess> processPage = resolvedFilter
                .map(item -> liveProcessPage(item, pageRequest, minLiveAt, liveProcessIds))
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

    private PageResponse<OpencodeServerProcess> liveProcessPage(
            OpencodeServerProcessFilter filter,
            PageRequest pageRequest,
            Instant minLiveAt,
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
                .filter(process -> heartbeatStore.enabled()
                        ? liveProcessIds.contains(process.processId())
                        : !process.lastHealthCheckAt().isBefore(minLiveAt))
                .toList();
        List<OpencodeServerProcess> pageItems = liveProcesses.stream()
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();
        return new PageResponse<>(pageItems, pageRequest.page(), pageRequest.size(), liveProcesses.size());
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

    private Optional<String> username(UserId userId) {
        return userRepository.findByUserId(userId).map(user -> user.username());
    }

    private static OpencodeProcessHeartbeatStore disabledHeartbeatStore() {
        return new OpencodeProcessHeartbeatStore() {
            @Override public boolean enabled() { return false; }
            @Override public void recordBackendHeartbeat(BackendProcessId backendProcessId, Instant heartbeatAt) { }
            @Override public void recordOpencodeHeartbeat(OpencodeProcessId processId, Instant heartbeatAt) { }
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
}
