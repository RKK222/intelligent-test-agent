package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 超级管理员运行管理只读查询服务，聚合拓扑、连接和用户进程绑定快照。
 */
@Service
public class RuntimeManagementQueryService {

    private static final int TOPOLOGY_LIMIT = 500;

    private final OpencodeProcessManagementRepository repository;
    private final Clock clock;

    /**
     * 生产构造器使用 UTC 系统时钟。
     */
    @Autowired
    public RuntimeManagementQueryService(OpencodeProcessManagementRepository repository) {
        this(repository, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟，避免快照时间不稳定。
     */
    public RuntimeManagementQueryService(OpencodeProcessManagementRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询管理页完整快照；traceId 预留给后续审计日志或指标，不影响只读结果。
     */
    public RuntimeManagementOverview overview(
            OpencodeServerProcessFilter filter,
            PageRequest pageRequest,
            String traceId) {
        var linuxServers = repository.findLinuxServers(TOPOLOGY_LIMIT);
        var backendProcesses = repository.findBackendJavaProcesses(TOPOLOGY_LIMIT);
        var containers = repository.findContainers(TOPOLOGY_LIMIT);
        var managers = repository.findContainerManagers(TOPOLOGY_LIMIT);
        var connections = repository.findManagerBackendConnections(TOPOLOGY_LIMIT);
        PageResponse<OpencodeServerProcess> processPage = repository.findOpencodeServerProcesses(filter, pageRequest);
        Map<OpencodeProcessId, UserOpencodeProcessBinding> bindings = repository.findUserBindingsByProcessIds(
                processPage.items().stream().map(OpencodeServerProcess::processId).toList());
        List<RuntimeManagementOpencodeProcess> rows = processPage.items().stream()
                .map(process -> new RuntimeManagementOpencodeProcess(process, Optional.ofNullable(bindings.get(process.processId()))))
                .toList();
        long runningOpencodeProcesses = runningProcessCount(filter);

        RuntimeManagementSummary summary = new RuntimeManagementSummary(
                linuxServers.size(),
                (int) linuxServers.stream().filter(server -> server.status() == LinuxServerStatus.READY).count(),
                backendProcesses.size(),
                (int) backendProcesses.stream().filter(process -> process.status() == BackendJavaProcessStatus.READY).count(),
                containers.size(),
                (int) containers.stream().filter(container -> container.status() == OpencodeContainerStatus.READY).count(),
                managers.size(),
                (int) managers.stream().filter(manager -> manager.connectionStatus() == ManagerConnectionStatus.CONNECTED).count(),
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

    private long runningProcessCount(OpencodeServerProcessFilter filter) {
        if (filter.status() != null && filter.status() != OpencodeServerProcessStatus.RUNNING) {
            return 0;
        }
        return repository.countOpencodeServerProcesses(new OpencodeServerProcessFilter(
                OpencodeServerProcessStatus.RUNNING,
                filter.linuxServerId(),
                filter.containerId(),
                filter.userId()));
    }
}
