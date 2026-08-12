package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 与单个已认证连接绑定的短期模型授权。 */
public record LocalClientModelGrant(
        String fingerprint,
        LocalClientInstanceId clientInstanceId,
        UserId userId,
        long connectionGeneration,
        Instant expiresAt) {

    public LocalClientModelGrant {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        Objects.requireNonNull(clientInstanceId, "clientInstanceId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        if (connectionGeneration < 1) {
            throw new IllegalArgumentException("connectionGeneration must be positive");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
