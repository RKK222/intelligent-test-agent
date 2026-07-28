package com.enterprise.testagent.domain.appsource;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import java.time.Instant;
import java.util.Objects;

/** 每个代码库唯一的 generation 分配槽位；lockVersion 用于数据库乐观并发控制。 */
public record AppSourceRepositorySlot(
        CodeRepositoryId repositoryId,
        Long activeGeneration,
        Long pendingGeneration,
        long nextGeneration,
        String latestOperationId,
        long lockVersion,
        Instant createdAt,
        Instant updatedAt) {

    public AppSourceRepositorySlot {
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        validateOptionalGeneration(activeGeneration, "activeGeneration");
        validateOptionalGeneration(pendingGeneration, "pendingGeneration");
        if (activeGeneration != null && activeGeneration.equals(pendingGeneration)) {
            throw new IllegalArgumentException("active and pending generation must differ");
        }
        long greatestAllocated = Math.max(activeGeneration == null ? 0L : activeGeneration,
                pendingGeneration == null ? 0L : pendingGeneration);
        if (nextGeneration < 1L || nextGeneration <= greatestAllocated) {
            throw new IllegalArgumentException("nextGeneration must be greater than allocated generations");
        }
        if (lockVersion < 0L) {
            throw new IllegalArgumentException("lockVersion must not be negative");
        }
        latestOperationId = optionalText(latestOperationId);
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public boolean matchesVersion(long expectedVersion) {
        return lockVersion == expectedVersion;
    }

    private static void validateOptionalGeneration(Long generation, String field) {
        if (generation != null && generation < 1L) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
