package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * internal_model_providers 表的 MyBatis 行模型。
 */
public record InternalModelProviderRow(
        String providerId,
        String name,
        String baseUrl,
        boolean enabled,
        int sortOrder,
        Long tokenId,
        String tokenName,
        boolean tokenConfigured,
        Instant createdAt,
        Instant updatedAt) {

    public InternalModelProviderRow(
            String providerId,
            String name,
            String baseUrl,
            boolean enabled,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt) {
        this(providerId, name, baseUrl, enabled, sortOrder, null, null, false, createdAt, updatedAt);
    }
}
