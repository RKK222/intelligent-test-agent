package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeMetrics;
import java.util.Objects;

/**
 * 运行管理后端 Java 进程行，组合进程拓扑字段和 Redis latest snapshot 中的最新指标。
 */
public record RuntimeManagementBackendProcess(
        BackendJavaProcess process,
        BackendRuntimeMetrics metrics) {

    public RuntimeManagementBackendProcess {
        process = Objects.requireNonNull(process, "process must not be null");
    }
}
