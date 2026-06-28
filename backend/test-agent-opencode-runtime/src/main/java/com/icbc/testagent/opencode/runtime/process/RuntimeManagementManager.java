package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import java.util.List;
import java.util.Objects;

/**
 * 运行管理页展示的一行 manager，附带该 manager 心跳上报的本地 opencode server 明细。
 */
public record RuntimeManagementManager(
        OpencodeContainerManager manager,
        List<RuntimeManagementManagedProcess> managedProcesses) {

    /**
     * 复制下属进程列表，兼容旧 Redis 快照中 managedProcesses 缺失的情况。
     */
    public RuntimeManagementManager {
        manager = Objects.requireNonNull(manager, "manager must not be null");
        managedProcesses = managedProcesses == null ? List.of() : List.copyOf(managedProcesses);
    }
}
