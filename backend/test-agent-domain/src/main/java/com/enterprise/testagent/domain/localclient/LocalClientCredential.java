package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 每用户唯一的本地客户端凭据；领域对象只保存密文、摘要和掩码提示。 */
public record LocalClientCredential(
        UserId userId,
        String encryptedClientKey,
        String clientKeyFingerprint,
        String keyHint,
        long version,
        LocalClientCredentialStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant revokedAt) {

    public LocalClientCredential {
        Objects.requireNonNull(userId, "userId must not be null");
        encryptedClientKey = DomainValidation.requireText(encryptedClientKey, "encryptedClientKey");
        clientKeyFingerprint = DomainValidation.requireText(clientKeyFingerprint, "clientKeyFingerprint");
        keyHint = DomainValidation.requireText(keyHint, "keyHint");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(status, "status must not be null");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (status == LocalClientCredentialStatus.ACTIVE && revokedAt != null) {
            throw new IllegalArgumentException("active credential must not have revokedAt");
        }
        if (status == LocalClientCredentialStatus.REVOKED && revokedAt == null) {
            throw new IllegalArgumentException("revoked credential must have revokedAt");
        }
    }

    public boolean active() {
        return status == LocalClientCredentialStatus.ACTIVE;
    }
}
