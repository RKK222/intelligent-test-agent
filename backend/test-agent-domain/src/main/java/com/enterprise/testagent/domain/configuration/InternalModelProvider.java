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
        Instant createdAt,
        Instant updatedAt) {

    public InternalModelProvider {
        providerId = DomainValidation.requireText(providerId, "providerId");
        name = DomainValidation.requireText(name, "name");
        baseUrl = DomainValidation.requireText(baseUrl, "baseUrl");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
