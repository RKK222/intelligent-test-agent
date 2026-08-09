package com.enterprise.testagent.domain.memory;

import java.time.Instant;
import java.util.Objects;

/** 可审计的记忆抽取设置；Embedding profile 由部署工件固定，不在原集合上热替换。 */
public record MemorySettings(
        String primaryChatModelId,
        boolean currentRunModelFallbackEnabled,
        long version,
        String updatedByUserId,
        Instant updatedAt) {
    public MemorySettings {
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
