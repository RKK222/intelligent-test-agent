package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 超级管理员运行管理页所需的完整只读快照。
 */
public record RuntimeManagementOverview(
        Instant generatedAt,
        RuntimeManagementSummary summary,
        List<LinuxServer> linuxServers,
        List<RuntimeManagementBackendProcess> backendProcesses,
        List<RuntimeManagementContainer> containers,
        List<RuntimeManagementManager> managers,
        List<OpencodeManagerBackendConnection> managerBackendConnections,
        PageResponse<RuntimeManagementOpencodeProcess> opencodeProcesses) {

    /**
     * 复制列表字段，防止 API 层映射期间外部继续修改快照。
     */
    public RuntimeManagementOverview {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        summary = Objects.requireNonNull(summary, "summary must not be null");
        linuxServers = List.copyOf(Objects.requireNonNull(linuxServers, "linuxServers must not be null"));
        backendProcesses = List.copyOf(Objects.requireNonNull(backendProcesses, "backendProcesses must not be null"));
        containers = List.copyOf(Objects.requireNonNull(containers, "containers must not be null"));
        managers = List.copyOf(Objects.requireNonNull(managers, "managers must not be null"));
        managerBackendConnections = List.copyOf(Objects.requireNonNull(managerBackendConnections, "managerBackendConnections must not be null"));
        opencodeProcesses = Objects.requireNonNull(opencodeProcesses, "opencodeProcesses must not be null");
    }
}
