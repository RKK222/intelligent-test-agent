package com.enterprise.testagent.domain.opencodeprocess;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 用户与 opencode server 进程的当前绑定，首期同一用户仅绑定一个 opencode agent 进程。
 */
public record UserOpencodeProcessBinding(
        UserId userId,
        String agentId,
        OpencodeProcessId processId,
        LinuxServerId linuxServerId,
        int port,
        UserOpencodeProcessBindingStatus status,
        Instant createdAt,
        Instant updatedAt,
        String traceId) {

    /**
     * 校验绑定必须指向 opencode agent，并保留服务器和端口用于后续粘滞调度。
     */
    public UserOpencodeProcessBinding {
        Objects.requireNonNull(userId, "userId must not be null");
        agentId = DomainValidation.requireText(agentId, "agentId").trim().toLowerCase(Locale.ROOT);
        if (!"opencode".equals(agentId)) {
            throw new IllegalArgumentException("agentId must be opencode");
        }
        Objects.requireNonNull(processId, "processId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        Objects.requireNonNull(status, "status must not be null");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
