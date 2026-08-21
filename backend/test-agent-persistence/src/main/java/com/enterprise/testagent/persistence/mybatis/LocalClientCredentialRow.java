package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** local_client_credentials 的 MyBatis 行模型。 */
public record LocalClientCredentialRow(
        String userId,
        String encryptedClientKey,
        String clientKeyFingerprint,
        String keyHint,
        long version,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant revealedAt,
        Instant revokedAt) {
}
