package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * 企业内部 OpenAI-compatible 模型供应商配置，模型清单仍由 opencode 配置文件维护。
 */
public record InternalModelProvider(
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

    public InternalModelProvider {
        providerId = DomainValidation.requireText(providerId, "providerId");
        name = DomainValidation.requireText(name, "name");
        baseUrl = DomainValidation.requireText(baseUrl, "baseUrl");
        if (tokenId != null && tokenId <= 0) {
            throw new IllegalArgumentException("tokenId must be positive");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * 兼容不携带 Token 关联的历史构造入口；新管理链路应显式传入 tokenId。
     */
    public InternalModelProvider(
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
