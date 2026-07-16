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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
}
