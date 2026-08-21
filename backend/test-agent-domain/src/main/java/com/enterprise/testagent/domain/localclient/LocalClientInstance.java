package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 本地客户端稳定实例及最近连接历史；实时在线状态只从连接存储读取。 */
public record LocalClientInstance(
        LocalClientInstanceId clientInstanceId,
        UserId userId,
        String clientName,
        String platform,
        String architecture,
        String clientVersion,
        String opencodeVersion,
        String launcherVersion,
        List<String> selfUpdateCapabilities,
        boolean selfUpdateSupported,
        String lastUpdateStatus,
        String lastUpdateTargetVersion,
        Instant lastUpdateAt,
        Instant createdAt,
        Instant updatedAt,
        Instant lastConnectedAt,
        Instant lastDisconnectedAt) {

    public LocalClientInstance {
        Objects.requireNonNull(clientInstanceId, "clientInstanceId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        clientName = DomainValidation.requireText(clientName, "clientName");
        platform = DomainValidation.requireText(platform, "platform");
        architecture = DomainValidation.requireText(architecture, "architecture");
        clientVersion = DomainValidation.requireText(clientVersion, "clientVersion");
        opencodeVersion = DomainValidation.requireText(opencodeVersion, "opencodeVersion");
        launcherVersion = normalizeOptional(launcherVersion);
        selfUpdateCapabilities = selfUpdateCapabilities == null ? List.of() : List.copyOf(selfUpdateCapabilities);
        lastUpdateStatus = normalizeOptional(lastUpdateStatus);
        lastUpdateTargetVersion = normalizeOptional(lastUpdateTargetVersion);
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
