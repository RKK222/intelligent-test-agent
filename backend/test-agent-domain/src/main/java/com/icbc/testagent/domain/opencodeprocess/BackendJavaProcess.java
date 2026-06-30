package com.icbc.testagent.domain.opencodeprocess;

import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * 后端 Java 进程实例快照，用于本机 manager 连接、后端路由和管理页展示。
 */
public record BackendJavaProcess(
        BackendProcessId backendProcessId,
        LinuxServerId linuxServerId,
        String listenUrl,
        BackendJavaProcessStatus status,
        Instant startedAt,
        Instant lastHeartbeatAt,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 校验后端进程所属服务器、监听地址和时间边界。
     */
    public BackendJavaProcess {
        Objects.requireNonNull(backendProcessId, "backendProcessId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        listenUrl = DomainValidation.requireText(listenUrl, "listenUrl");
        Objects.requireNonNull(status, "status must not be null");
        startedAt = DomainValidation.requireInstant(startedAt, "startedAt");
        lastHeartbeatAt = DomainValidation.requireInstant(lastHeartbeatAt, "lastHeartbeatAt");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
