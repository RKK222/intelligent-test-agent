package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.domain.opencodeprocess.ContainerRuntimeMetrics;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import java.util.Objects;

/**
 * 运行管理容器行，组合持久拓扑字段和 Redis latest snapshot 中的最新指标。
 */
public record RuntimeManagementContainer(
        OpencodeContainer container,
        ContainerRuntimeMetrics metrics) {

    public RuntimeManagementContainer {
        container = Objects.requireNonNull(container, "container must not be null");
    }
}
