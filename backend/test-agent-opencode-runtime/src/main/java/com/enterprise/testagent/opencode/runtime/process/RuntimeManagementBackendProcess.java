package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeMetrics;
import java.util.Objects;

/**
 * 运行管理后端 Java 进程行，组合进程拓扑字段和 Redis latest snapshot 中的最新指标。
 */
public record RuntimeManagementBackendProcess(
        BackendJavaProcess process,
        BackendRuntimeMetrics metrics,
        String buildVersion) {

    /**
     * 兼容旧构造调用，旧快照没有构建版本时保持为空。
     */
    public RuntimeManagementBackendProcess(BackendJavaProcess process, BackendRuntimeMetrics metrics) {
        this(process, metrics, null);
    }

    public RuntimeManagementBackendProcess {
        process = Objects.requireNonNull(process, "process must not be null");
    }
}
