package com.icbc.testagent.domain.opencodeprocess;

import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * 用户专属 opencode server 进程快照，记录端口、PID、启动路径和健康信息。
 */
public record OpencodeServerProcess(
        OpencodeProcessId processId,
        UserId userId,
        LinuxServerId linuxServerId,
        OpencodeContainerId containerId,
        int port,
        Long pid,
        String baseUrl,
        OpencodeServerProcessStatus status,
        String sessionPath,
        String configPath,
        Instant startedAt,
        Instant lastHealthCheckAt,
        String healthMessage,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 校验进程必须绑定用户、服务器、容器和主机直通端口。
     */
    public OpencodeServerProcess {
        Objects.requireNonNull(processId, "processId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        Objects.requireNonNull(containerId, "containerId must not be null");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        baseUrl = DomainValidation.requireText(baseUrl, "baseUrl");
        String expectedBaseUrl = "http://" + linuxServerId.value() + ":" + port;
        if (!expectedBaseUrl.equals(baseUrl)) {
            throw new IllegalArgumentException("baseUrl must equal " + expectedBaseUrl);
        }
        Objects.requireNonNull(status, "status must not be null");
        sessionPath = DomainValidation.requireText(sessionPath, "sessionPath");
        configPath = DomainValidation.requireText(configPath, "configPath");
        startedAt = DomainValidation.requireInstant(startedAt, "startedAt");
        lastHealthCheckAt = DomainValidation.requireInstant(lastHealthCheckAt, "lastHealthCheckAt");
        healthMessage = healthMessage == null ? "" : healthMessage;
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
