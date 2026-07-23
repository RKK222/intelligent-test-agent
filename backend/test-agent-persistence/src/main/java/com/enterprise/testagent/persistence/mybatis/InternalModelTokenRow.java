package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * internal_model_tokens 表的安全元数据行，不读取 Token 明文。
 */
public record InternalModelTokenRow(
        long tokenId,
        String name,
        long referencedProviderCount,
        Instant createdAt,
        Instant updatedAt) {
}
