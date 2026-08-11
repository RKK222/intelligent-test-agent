package com.enterprise.testagent.domain.memory;

import java.time.Instant;
import java.util.Objects;

/** 真正注入当前 Run 的记忆使用记录；检索命中但未注入时不得创建。 */
public record MemoryUsage(
        String runId,
        MemoryId memoryId,
        String userId,
        String applicationId,
        MemoryScope scope,
        int rank,
        int tokenCount,
        Instant injectedAt) {

    public MemoryUsage {
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(injectedAt, "injectedAt must not be null");
        if (rank < 1 || tokenCount < 0) {
            throw new IllegalArgumentException("rank must be positive and tokenCount non-negative");
        }
    }
}
