package com.enterprise.testagent.domain.memory;

import java.time.Instant;
import java.util.Objects;

/** 记忆模型设置：固定 CHAT、可空企业 Embedding 和始终存在的 CPU BGE 备用。 */
public record MemorySettings(
        String primaryChatModelId,
        String primaryEmbeddingModelId,
        String cpuEmbeddingModelId,
        long version,
        String updatedByUserId,
        Instant updatedAt) {
    public MemorySettings {
        primaryChatModelId = optional(primaryChatModelId);
        primaryEmbeddingModelId = optional(primaryEmbeddingModelId);
        cpuEmbeddingModelId = required(cpuEmbeddingModelId, "cpuEmbeddingModelId");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
