package com.enterprise.testagent.domain.opencodeprocess;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 容器管理进程快照，每个 opencode 容器首期只允许一个管理进程。
 */
public record OpencodeContainerManager(
        ContainerManagerId managerId,
        OpencodeContainerId containerId,
        LinuxServerId linuxServerId,
        String protocolVersion,
        ManagerConnectionStatus connectionStatus,
        Map<String, Object> capabilities,
        Instant lastHeartbeatAt,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 校验管理进程绑定容器、协议版本和连接状态。
     */
    public OpencodeContainerManager {
        Objects.requireNonNull(managerId, "managerId must not be null");
        Objects.requireNonNull(containerId, "containerId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        protocolVersion = DomainValidation.requireText(protocolVersion, "protocolVersion");
        Objects.requireNonNull(connectionStatus, "connectionStatus must not be null");
        capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
        lastHeartbeatAt = DomainValidation.requireInstant(lastHeartbeatAt, "lastHeartbeatAt");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
