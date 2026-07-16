package com.enterprise.testagent.domain.opencodeprocess;

import java.util.List;
import java.util.Objects;

/**
 * Redis 中的 opencode-manager 运行快照；TTL 表示 manager 在线，连接列表表示当前可达后端。
 */
public record ManagerRuntimeSnapshot(
        OpencodeContainer container,
        OpencodeContainerManager manager,
        List<OpencodeManagerBackendConnection> connections,
        ContainerRuntimeMetrics metrics,
        List<ManagedOpencodeProcessSnapshot> managedProcesses,
        String buildVersion) {

    /**
     * 兼容旧调用方和旧 Redis JSON，新增构建版本缺失时保持为空。
     */
    public ManagerRuntimeSnapshot(
            OpencodeContainer container,
            OpencodeContainerManager manager,
            List<OpencodeManagerBackendConnection> connections,
            ContainerRuntimeMetrics metrics,
            List<ManagedOpencodeProcessSnapshot> managedProcesses) {
        this(container, manager, connections, metrics, managedProcesses, null);
    }

    /**
     * 兼容旧调用方，未上报资源指标时只保存拓扑和连接。
     */
    public ManagerRuntimeSnapshot(
            OpencodeContainer container,
            OpencodeContainerManager manager,
            List<OpencodeManagerBackendConnection> connections) {
        this(container, manager, connections, null, List.of(), null);
    }

    /**
     * 复制连接列表，避免调用方后续修改影响运行管理快照。
     */
    public ManagerRuntimeSnapshot {
        Objects.requireNonNull(container, "container must not be null");
        Objects.requireNonNull(manager, "manager must not be null");
        connections = connections == null ? List.of() : List.copyOf(connections);
        managedProcesses = managedProcesses == null ? List.of() : List.copyOf(managedProcesses);
    }
}
