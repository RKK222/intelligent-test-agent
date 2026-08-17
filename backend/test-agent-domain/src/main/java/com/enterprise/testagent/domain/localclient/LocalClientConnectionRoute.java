package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Redis 中短 TTL 的精确连接路由；上报地址只用于展示，不能参与转发决策。 */
public record LocalClientConnectionRoute(
        LocalClientInstanceId clientInstanceId,
        UserId userId,
        BackendProcessId backendProcessId,
        long connectionGeneration,
        String observedRemoteAddress,
        List<String> reportedAddresses,
        Integer opencodePort,
        LocalClientProcessStatus processStatus,
        Long processId,
        Instant processStartedAt,
        boolean opencodeHealthy,
        Instant connectedAt,
        Instant lastHeartbeatAt) {

    public LocalClientConnectionRoute {
        Objects.requireNonNull(clientInstanceId, "clientInstanceId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(backendProcessId, "backendProcessId must not be null");
        if (connectionGeneration < 1) {
            throw new IllegalArgumentException("connectionGeneration must be positive");
        }
        observedRemoteAddress = normalizeOptional(observedRemoteAddress);
        reportedAddresses = reportedAddresses == null ? List.of() : List.copyOf(reportedAddresses);
        Objects.requireNonNull(processStatus, "processStatus must not be null");
        connectedAt = Objects.requireNonNull(connectedAt, "connectedAt must not be null");
        lastHeartbeatAt = Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt must not be null");
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
