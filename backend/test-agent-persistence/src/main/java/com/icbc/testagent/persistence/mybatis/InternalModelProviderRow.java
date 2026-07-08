package com.icbc.testagent.persistence.mybatis;

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
        Instant createdAt,
        Instant updatedAt) {
}
