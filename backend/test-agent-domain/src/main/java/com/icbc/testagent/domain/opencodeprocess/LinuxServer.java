package com.icbc.testagent.domain.opencodeprocess;

import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Linux 服务器运行拓扑快照，服务器 ID 直接使用可访问 opencode 端口的 IP 地址。
 */
public record LinuxServer(
        LinuxServerId linuxServerId,
        String name,
        LinuxServerStatus status,
        Map<String, Object> capacitySummary,
        Instant lastHeartbeatAt,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 校验服务器基础信息，并复制容量摘要避免外部修改。
     */
    public LinuxServer {
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        name = DomainValidation.requireText(name, "name");
        Objects.requireNonNull(status, "status must not be null");
        capacitySummary = capacitySummary == null ? Map.of() : Map.copyOf(capacitySummary);
        lastHeartbeatAt = DomainValidation.requireInstant(lastHeartbeatAt, "lastHeartbeatAt");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
