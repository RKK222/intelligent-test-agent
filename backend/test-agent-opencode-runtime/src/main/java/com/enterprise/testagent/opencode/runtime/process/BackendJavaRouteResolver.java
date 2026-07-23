package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServer;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.enterprise.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 后端 Java 路由解析器，集中处理当前服务器身份、Redis 后端快照和 manager 容器归属。
 */
@Service
public class BackendJavaRouteResolver {

    private static final int DEFAULT_BACKEND_LIMIT = 500;

    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final ManagerControlSettings settings;
    private final BackendProcessId currentBackendProcessId;
    private final Clock clock;

    /**
     * 生产构造器使用 UTC 系统时钟。
     */
    @Autowired
    public BackendJavaRouteResolver(
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings,
            BackendJavaProcessLifecycleService lifecycleService) {
        this(heartbeatStore, settings, lifecycleService.backendProcessId(), Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟。
     */
    public BackendJavaRouteResolver(
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings,
            Clock clock) {
        this(heartbeatStore, settings, new BackendProcessId("bjp_current_backend"), clock);
    }

    /**
     * 测试构造器允许显式指定当前 Java backendProcessId。
     */
    public BackendJavaRouteResolver(
            OpencodeProcessHeartbeatStore heartbeatStore,
            ManagerControlSettings settings,
            BackendProcessId currentBackendProcessId,
            Clock clock) {
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.currentBackendProcessId = Objects.requireNonNull(currentBackendProcessId, "currentBackendProcessId must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 返回当前 Java 控制的 Linux 服务器身份。
     */
    public LinuxServerId currentLinuxServerId() {
        return settings.linuxServerId();
    }

    /**
     * 返回当前 Java 控制的 Linux 服务器身份字符串。
     */
    public String currentLinuxServerIdValue() {
        return currentLinuxServerId().value();
    }

    /**
     * 返回当前 Java 的稳定进程身份，供 Run owner lease 区分同服务器上的多个 Java。
     */
    public BackendProcessId currentBackendProcessId() {
        return currentBackendProcessId;
    }

    /**
     * 返回当前 Java 的稳定进程身份字符串。
     */
    public String currentBackendProcessIdValue() {
        return currentBackendProcessId.value();
    }

    /**
     * 判断目标服务器是否就是当前 Java。
     */
    public boolean isCurrent(LinuxServerId linuxServerId) {
        return currentLinuxServerId().equals(linuxServerId);
    }

    /**
     * 判断目标服务器是否就是当前 Java。
     */
    public boolean isCurrent(String linuxServerId) {
        return linuxServerId != null && currentLinuxServerIdValue().equals(linuxServerId.trim());
    }

    /**
     * 按稳定进程 ID 判断目标是否就是当前 Java，避免同服务器多 Java 被误判成本机。
     */
    public boolean isCurrent(BackendProcessId backendProcessId) {
        return backendProcessId != null && currentBackendProcessId.equals(backendProcessId);
    }

    /**
     * 只有目标不是当前 Java 时返回远端路由目标。
     */
    public Optional<LinuxServerId> remoteTarget(LinuxServerId linuxServerId) {
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        if (!isCurrent(linuxServerId)) {
            return Optional.of(linuxServerId);
        }
        BackendJavaProcess selected = requireBackend(linuxServerId);
        return isCurrentBackend(selected) ? Optional.empty() : Optional.of(linuxServerId);
    }

    /**
     * 只有目标不是当前 Java 时返回远端路由目标。
     */
    public Optional<String> remoteTarget(String linuxServerId) {
        if (linuxServerId == null || linuxServerId.isBlank()) {
            return Optional.empty();
        }
        LinuxServerId parsed = new LinuxServerId(linuxServerId.trim());
        return remoteTarget(parsed).map(LinuxServerId::value);
    }

    /**
     * 查询目标服务器最新在线 Java；当前服务器没有 Redis 快照时使用本地 listenUrl 兜底。
     */
    public BackendJavaProcess requireBackend(LinuxServerId linuxServerId) {
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        if (isCurrent(linuxServerId)) {
            return latestBackendsByServer().getOrDefault(linuxServerId.value(), currentBackend());
        }
        BackendJavaProcess backend = latestBackendsByServer().get(linuxServerId.value());
        if (backend == null) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "目标服务器后端不可用",
                    Map.of("linuxServerId", linuxServerId.value()));
        }
        return backend;
    }

    /**
     * 查询目标服务器最新在线 Java。
     */
    public BackendJavaProcess requireBackend(String linuxServerId) {
        if (linuxServerId == null || linuxServerId.isBlank()) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "目标服务器后端不可用",
                    Map.of("linuxServerId", ""));
        }
        return requireBackend(new LinuxServerId(linuxServerId.trim()));
    }

    /**
     * 按 backendProcessId 精确查询在线 Java；当前进程无需依赖 Redis 快照即可命中。
     */
    public BackendJavaProcess requireBackend(BackendProcessId backendProcessId) {
        Objects.requireNonNull(backendProcessId, "backendProcessId must not be null");
        if (isCurrent(backendProcessId)) {
            return currentBackend();
        }
        return heartbeatStore.liveBackendSnapshots().stream()
                .map(BackendRuntimeSnapshot::backendProcess)
                .filter(backend -> backend.backendProcessId().equals(backendProcessId))
                .max(Comparator.comparing(BackendJavaProcess::lastHeartbeatAt))
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "目标 Java 进程不可用",
                        Map.of("backendProcessId", backendProcessId.value())));
    }

    /**
     * 返回每台服务器最新在线 Java，包含当前 Java 的本地兜底。
     */
    public Map<String, BackendJavaProcess> liveBackendsByServer() {
        Map<String, BackendJavaProcess> result = latestBackendsByServer();
        result.putIfAbsent(currentLinuxServerIdValue(), currentBackend());
        return result;
    }

    /**
     * 为尚未绑定用户选择当前可初始化且进程总数最少的 Linux 服务器。
     *
     * <p>负载按同一轮 Redis 最新 manager 快照汇总服务器上的全部在线容器，已满容器仍计入
     * 进程总数；但服务器必须至少存在一个由所选在线 Java 连接、状态 READY 且仍有容量的容器。
     * 该结果只负责首次请求选服，目标 Java 仍需通过本机候选解析和原子预留复核真实容量。
     */
    public Optional<LinuxServerId> selectLeastLoadedInitializableServer() {
        List<ManagerRuntimeSnapshot> managerSnapshots;
        List<BackendRuntimeSnapshot> backendSnapshots;
        try {
            managerSnapshots = heartbeatStore.liveManagerSnapshots();
            backendSnapshots = heartbeatStore.liveBackendSnapshots();
        } catch (RuntimeException exception) {
            throw new PlatformException(
                    ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                    "TestAgent Redis 运行态候选不可用",
                    Map.of(),
                    exception);
        }

        Map<OpencodeContainerId, ManagerRuntimeSnapshot> latestManagers = latestManagerSnapshots(managerSnapshots);
        Map<String, BackendJavaProcess> selectedBackends = latestBackendsByServer(
                backendSnapshots,
                List.copyOf(latestManagers.values()));
        selectedBackends.putIfAbsent(currentLinuxServerIdValue(), currentBackend());

        Map<String, Long> processTotals = new HashMap<>();
        Set<String> initializableServers = new HashSet<>();
        for (ManagerRuntimeSnapshot snapshot : latestManagers.values()) {
            if (snapshot.manager().connectionStatus() != ManagerConnectionStatus.CONNECTED) {
                continue;
            }
            OpencodeContainer container = snapshot.container();
            String linuxServerId = container.linuxServerId().value();
            processTotals.merge(linuxServerId, (long) container.currentProcesses(), Long::sum);

            BackendJavaProcess selectedBackend = selectedBackends.get(linuxServerId);
            if (selectedBackend != null
                    && container.canAcceptProcess()
                    && isConnected(snapshot, selectedBackend.backendProcessId())) {
                initializableServers.add(linuxServerId);
            }
        }

        return initializableServers.stream()
                .sorted(Comparator.comparingLong(
                                (String linuxServerId) -> processTotals.getOrDefault(linuxServerId, Long.MAX_VALUE))
                        .thenComparing(Comparator.naturalOrder()))
                .findFirst()
                .map(LinuxServerId::new);
    }

    /**
     * 返回远端服务器最新在线 Java。
     */
    public Map<String, BackendJavaProcess> remoteBackendsByServer() {
        Map<String, BackendJavaProcess> result = liveBackendsByServer();
        result.remove(currentLinuxServerIdValue());
        return result;
    }

    /**
     * 返回在线 Java 快照，包含当前 Java 的本地兜底。
     */
    public List<BackendRuntimeSnapshot> liveBackendSnapshots(int limit) {
        Map<String, BackendRuntimeSnapshot> snapshotsByBackend = new LinkedHashMap<>();
        for (BackendRuntimeSnapshot snapshot : heartbeatStore.liveBackendSnapshots()) {
            snapshotsByBackend.put(snapshot.backendProcess().backendProcessId().value(), snapshot);
        }
        snapshotsByBackend.putIfAbsent(currentBackendProcessId.value(), currentBackendSnapshot());
        int resolvedLimit = limit < 1 ? DEFAULT_BACKEND_LIMIT : limit;
        return snapshotsByBackend.values().stream()
                .limit(resolvedLimit)
                .toList();
    }

    /**
     * 按最新 manager 快照定位容器归属服务器。
     */
    public Optional<LinuxServerId> containerLinuxServerId(OpencodeContainerId containerId) {
        Objects.requireNonNull(containerId, "containerId must not be null");
        return heartbeatStore.liveManagerSnapshots().stream()
                .map(ManagerRuntimeSnapshot::container)
                .filter(container -> container.containerId().equals(containerId))
                .max(Comparator.comparing(OpencodeContainer::lastHeartbeatAt))
                .map(OpencodeContainer::linuxServerId);
    }

    private Map<String, BackendJavaProcess> latestBackendsByServer() {
        return latestBackendsByServer(
                heartbeatStore.liveBackendSnapshots(),
                heartbeatStore.liveManagerSnapshots());
    }

    private Map<String, BackendJavaProcess> latestBackendsByServer(
            List<BackendRuntimeSnapshot> backendSnapshots,
            List<ManagerRuntimeSnapshot> managerSnapshots) {
        Map<String, BackendJavaProcess> result = new LinkedHashMap<>();
        Map<String, Set<BackendProcessId>> connectedBackendIdsByServer =
                connectedBackendIdsByServer(managerSnapshots);
        for (BackendRuntimeSnapshot snapshot : backendSnapshots) {
            BackendJavaProcess backend = snapshot.backendProcess();
            result.merge(
                    backend.linuxServerId().value(),
                    backend,
                    (left, right) -> preferredBackend(left, right, connectedBackendIdsByServer));
        }
        return result;
    }

    private BackendJavaProcess preferredBackend(
            BackendJavaProcess left,
            BackendJavaProcess right,
            Map<String, Set<BackendProcessId>> connectedBackendIdsByServer) {
        Set<BackendProcessId> connected = connectedBackendIdsByServer.getOrDefault(left.linuxServerId().value(), Set.of());
        boolean leftConnected = connected.contains(left.backendProcessId());
        boolean rightConnected = connected.contains(right.backendProcessId());
        if (leftConnected != rightConnected) {
            return rightConnected ? right : left;
        }
        return latestBackend(left, right);
    }

    private Map<String, Set<BackendProcessId>> connectedBackendIdsByServer(
            List<ManagerRuntimeSnapshot> managerSnapshots) {
        Map<String, Set<BackendProcessId>> result = new HashMap<>();
        for (ManagerRuntimeSnapshot snapshot : managerSnapshots) {
            if (snapshot == null) {
                continue;
            }
            String linuxServerId = snapshot.manager().linuxServerId().value();
            for (OpencodeManagerBackendConnection connection : snapshot.connections()) {
                if (connection.status() == ManagerConnectionStatus.CONNECTED) {
                    result.computeIfAbsent(linuxServerId, ignored -> new HashSet<>()).add(connection.backendProcessId());
                }
            }
        }
        return result;
    }

    private Map<OpencodeContainerId, ManagerRuntimeSnapshot> latestManagerSnapshots(
            List<ManagerRuntimeSnapshot> snapshots) {
        Map<OpencodeContainerId, ManagerRuntimeSnapshot> latestByContainer = new LinkedHashMap<>();
        for (ManagerRuntimeSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            latestByContainer.merge(
                    snapshot.container().containerId(),
                    snapshot,
                    (left, right) -> right.container().lastHeartbeatAt()
                                    .isBefore(left.container().lastHeartbeatAt())
                            ? left
                            : right);
        }
        return latestByContainer;
    }

    private boolean isConnected(
            ManagerRuntimeSnapshot snapshot,
            BackendProcessId backendProcessId) {
        return snapshot.connections().stream()
                .anyMatch(connection -> connection.backendProcessId().equals(backendProcessId)
                        && connection.status() == ManagerConnectionStatus.CONNECTED);
    }

    private BackendJavaProcess latestBackend(BackendJavaProcess left, BackendJavaProcess right) {
        return right.lastHeartbeatAt().isAfter(left.lastHeartbeatAt()) ? right : left;
    }

    private boolean isCurrentBackend(BackendJavaProcess backend) {
        return backend.backendProcessId().equals(currentBackendProcessId)
                || Objects.equals(backend.listenUrl(), settings.listenUrl());
    }

    private BackendRuntimeSnapshot currentBackendSnapshot() {
        Instant now = Instant.now(clock);
        LinuxServer server = new LinuxServer(
                currentLinuxServerId(),
                currentLinuxServerIdValue(),
                LinuxServerStatus.READY,
                Map.of("backendListenUrl", settings.listenUrl()),
                now,
                now,
                now,
                "trace_current_backend");
        return new BackendRuntimeSnapshot(server, currentBackend(now));
    }

    private BackendJavaProcess currentBackend() {
        return currentBackend(Instant.now(clock));
    }

    private BackendJavaProcess currentBackend(Instant now) {
        return new BackendJavaProcess(
                currentBackendProcessId,
                currentLinuxServerId(),
                settings.listenUrl(),
                BackendJavaProcessStatus.READY,
                now,
                now,
                now,
                now,
                "trace_current_backend");
    }
}
