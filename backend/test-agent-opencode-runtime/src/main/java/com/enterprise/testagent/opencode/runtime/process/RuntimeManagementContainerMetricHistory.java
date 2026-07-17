package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 运行管理容器指标历史响应模型。
 */
public record RuntimeManagementContainerMetricHistory(
        Instant generatedAt,
        OpencodeContainerId containerId,
        Instant from,
        Instant to,
        List<RuntimeManagementContainerMetricSample> samples) {

    public RuntimeManagementContainerMetricHistory {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        containerId = Objects.requireNonNull(containerId, "containerId must not be null");
        from = Objects.requireNonNull(from, "from must not be null");
        to = Objects.requireNonNull(to, "to must not be null");
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
