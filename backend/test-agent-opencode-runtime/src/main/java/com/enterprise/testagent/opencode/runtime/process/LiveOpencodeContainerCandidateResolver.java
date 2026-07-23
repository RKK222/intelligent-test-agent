package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerConnectionRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 从 Redis manager TTL 快照解析当前 Java 实例可实际下发命令的容器候选。
 */
@Component
public class LiveOpencodeContainerCandidateResolver {

    private static final int MAX_CANDIDATES = 100;

    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final BackendJavaProcessLifecycleService backendLifecycle;
    private final ManagerConnectionRegistry connectionRegistry;

    public LiveOpencodeContainerCandidateResolver(
            OpencodeProcessHeartbeatStore heartbeatStore,
            BackendJavaProcessLifecycleService backendLifecycle,
            ManagerConnectionRegistry connectionRegistry) {
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.backendLifecycle = Objects.requireNonNull(backendLifecycle, "backendLifecycle must not be null");
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry, "connectionRegistry must not be null");
    }

    /**
     * 返回当前 Java 可调度的全局候选，结果最多包含 100 个容器。
     */
    public List<OpencodeContainer> findCandidates(int limit) {
        return findCandidates(null, limit);
    }

    /**
     * 返回指定原服务器内可重建的候选，避免已有 binding 在重建时跨服务器迁移。
     */
    public List<OpencodeContainer> findCandidates(LinuxServerId requiredLinuxServerId, int limit) {
        int resolvedLimit = Math.min(Math.max(limit, 0), MAX_CANDIDATES);
        if (resolvedLimit == 0) {
            return List.of();
        }
        List<ManagerRuntimeSnapshot> snapshots;
        try {
            snapshots = heartbeatStore.liveManagerSnapshots();
        } catch (RuntimeException exception) {
            throw new PlatformException(
                    ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                    "TestAgent Redis 运行态候选不可用",
                    Map.of(),
                    exception);
        }

        BackendProcessId currentBackend = backendLifecycle.backendProcessId();
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

        List<OpencodeContainer> candidates = new ArrayList<>();
        for (ManagerRuntimeSnapshot snapshot : latestByContainer.values()) {
            OpencodeContainer container = snapshot.container();
            boolean connectedToCurrentBackend = snapshot.connections().stream()
                    .anyMatch(connection -> connection.backendProcessId().equals(currentBackend)
                            && connection.status() == ManagerConnectionStatus.CONNECTED);
            if (snapshot.manager().connectionStatus() != ManagerConnectionStatus.CONNECTED
                    || !container.canAcceptProcess()
                    || !connectedToCurrentBackend
                    || !connectionRegistry.isConnected(container.containerId())
                    || requiredLinuxServerId != null
                            && !container.linuxServerId().equals(requiredLinuxServerId)) {
                continue;
            }
            candidates.add(container);
        }

        return candidates.stream()
                .sorted(Comparator.comparingInt(OpencodeContainer::currentProcesses)
                        .thenComparing(container -> container.containerId().value()))
                .limit(resolvedLimit)
                .toList();
    }

    /**
     * 精确解析既有绑定的容器。绑定端口已被分配，因此忽略容量是否已满；
     * 仍要求 Redis 中 manager 存活、连接当前 Java，且当前 Java 持有本地 WebSocket。
     */
    public Optional<OpencodeContainer> findExactBoundContainer(
            LinuxServerId linuxServerId,
            OpencodeContainerId containerId) {
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        Objects.requireNonNull(containerId, "containerId must not be null");
        List<ManagerRuntimeSnapshot> snapshots;
        try {
            snapshots = heartbeatStore.liveManagerSnapshots();
        } catch (RuntimeException exception) {
            throw new PlatformException(
                    ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                    "TestAgent Redis 运行态候选不可用",
                    Map.of(),
                    exception);
        }
        BackendProcessId currentBackend = backendLifecycle.backendProcessId();
        Optional<ManagerRuntimeSnapshot> latestSnapshot = snapshots.stream()
                .filter(Objects::nonNull)
                .filter(snapshot -> snapshot.container().linuxServerId().equals(linuxServerId))
                .filter(snapshot -> snapshot.container().containerId().equals(containerId))
                .max(Comparator.comparing(snapshot -> snapshot.container().lastHeartbeatAt()));
        if (latestSnapshot.isEmpty()) {
            return Optional.empty();
        }
        ManagerRuntimeSnapshot snapshot = latestSnapshot.orElseThrow();
        OpencodeContainer container = snapshot.container();
        boolean connectedToCurrentBackend = snapshot.connections().stream()
                .anyMatch(connection -> connection.backendProcessId().equals(currentBackend)
                        && connection.status() == ManagerConnectionStatus.CONNECTED);
        if (snapshot.manager().connectionStatus() != ManagerConnectionStatus.CONNECTED
                || container.status()
                        != com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerStatus.READY
                || !connectedToCurrentBackend
                || !connectionRegistry.isConnected(containerId)) {
            return Optional.empty();
        }
        return Optional.of(container);
    }

    /**
     * 汇总同一 Linux 服务器上所有 live manager 快照中的受管端口。
     *
     * <p>即使数据库尚未记录该进程也必须避让；这里不按容器或容量过滤，避免端口唯一约束的
     * 服务器级作用域被历史容器归属掩盖。
     */
    public Set<Integer> liveManagedPorts(LinuxServerId linuxServerId) {
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        try {
            Set<Integer> ports = new HashSet<>();
            heartbeatStore.liveManagerSnapshots().stream()
                    .filter(Objects::nonNull)
                    .filter(snapshot -> snapshot.container().linuxServerId().equals(linuxServerId))
                    .flatMap(snapshot -> snapshot.managedProcesses().stream())
                    .map(process -> process.port())
                    .filter(port -> port > 0 && port <= 65535)
                    .forEach(ports::add);
            return Set.copyOf(ports);
        } catch (RuntimeException exception) {
            throw new PlatformException(
                    ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                    "TestAgent Redis 运行态端口不可用",
                    Map.of(),
                    exception);
        }
    }
}
