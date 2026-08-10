package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 外部 API 凭据表行模型，不对 API 层暴露。 */
public record ExternalApiCredentialRow(
        String credentialId,
        String toolCode,
        String toolName,
        String encryptedApiKey,
        String apiKeyFingerprint,
        String keyHint,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
