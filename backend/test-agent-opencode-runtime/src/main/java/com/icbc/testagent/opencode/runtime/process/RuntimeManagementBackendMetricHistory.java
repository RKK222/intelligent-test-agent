package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 运行管理后端 Java 进程指标历史响应模型。
 */
public record RuntimeManagementBackendMetricHistory(
        Instant generatedAt,
        BackendProcessId backendProcessId,
        Instant from,
        Instant to,
        List<RuntimeManagementBackendMetricSample> samples) {

    public RuntimeManagementBackendMetricHistory {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        backendProcessId = Objects.requireNonNull(backendProcessId, "backendProcessId must not be null");
        from = Objects.requireNonNull(from, "from must not be null");
        to = Objects.requireNonNull(to, "to must not be null");
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
