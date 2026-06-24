package com.icbc.testagent.domain.model;

import com.icbc.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 平台可选大模型配置，供内网模式从数据库返回模型列表并驱动 opencode provider 配置同步。
 */
public record AiModelConfig(
        String providerId,
        String modelId,
        String name,
        boolean enabled,
        boolean defaultModel,
        Set<String> inputModalities,
        int contextLimit,
        int outputLimit,
        int sortOrder,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 校验模型配置基础不变量，并复制集合/Map，避免外部可变对象进入领域层。
     */
    public AiModelConfig {
        providerId = DomainValidation.requireText(providerId, "providerId");
        modelId = DomainValidation.requireText(modelId, "modelId");
        name = DomainValidation.requireText(name, "name");
        inputModalities = inputModalities == null || inputModalities.isEmpty() ? Set.of("text") : Set.copyOf(inputModalities);
        if (contextLimit < 1) {
            throw new IllegalArgumentException("contextLimit must be greater than 0");
        }
        if (outputLimit < 1) {
            throw new IllegalArgumentException("outputLimit must be greater than 0");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        Objects.requireNonNull(inputModalities, "inputModalities must not be null");
    }
}
