package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * 可复用的内部模型 Token 安全元数据，不包含外部系统提供的 Token 明文。
 */
public record InternalModelToken(
        long tokenId,
        String name,
        long referencedProviderCount,
        Instant createdAt,
        Instant updatedAt) {

    public InternalModelToken {
        if (tokenId <= 0) {
            throw new IllegalArgumentException("tokenId must be positive");
        }
        name = DomainValidation.requireText(name, "name");
        if (referencedProviderCount < 0) {
            throw new IllegalArgumentException("referencedProviderCount must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
