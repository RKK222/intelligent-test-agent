package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 运行管理后端 Java 进程指标历史响应模型。
 */
public record RuntimeManagementBackendMetricHistory(
        Instant generatedAt,
        LinuxServerId linuxServerId,
        Optional<BackendProcessId> backendProcessId,
        Instant from,
        Instant to,
        List<RuntimeManagementBackendMetricSample> samples) {

    public RuntimeManagementBackendMetricHistory {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        backendProcessId = backendProcessId == null ? Optional.empty() : backendProcessId;
        from = Objects.requireNonNull(from, "from must not be null");
        to = Objects.requireNonNull(to, "to must not be null");
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
