package com.icbc.testagent.domain.opencodeprocess;

import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * 管理进程到后端 Java 实例的连接快照，用于后续支持多后端实例全连接。
 */
public record OpencodeManagerBackendConnection(
        ContainerManagerId managerId,
        BackendProcessId backendProcessId,
        ManagerConnectionStatus status,
        Instant connectedAt,
        Instant lastHeartbeatAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 校验连接两端和心跳时间。
     */
    public OpencodeManagerBackendConnection {
        Objects.requireNonNull(managerId, "managerId must not be null");
        Objects.requireNonNull(backendProcessId, "backendProcessId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        connectedAt = DomainValidation.requireInstant(connectedAt, "connectedAt");
        lastHeartbeatAt = DomainValidation.requireInstant(lastHeartbeatAt, "lastHeartbeatAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
