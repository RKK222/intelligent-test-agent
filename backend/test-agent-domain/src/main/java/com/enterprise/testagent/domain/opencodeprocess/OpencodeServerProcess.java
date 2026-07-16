package com.enterprise.testagent.domain.opencodeprocess;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.net.URI;
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
        validateBaseUrl(baseUrl, port);
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

    /**
     * baseUrl 表示网络地址，不能再与稳定服务器 ID 绑定；这里只保证协议、主机和端口可用。
     */
    private static void validateBaseUrl(String baseUrl, int port) {
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("baseUrl scheme must be http or https");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("baseUrl host must not be blank");
            }
            if (uri.getPort() != port) {
                throw new IllegalArgumentException("baseUrl port must equal process port");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("baseUrl must be an absolute http(s) URL with matching port", exception);
        }
    }
}
