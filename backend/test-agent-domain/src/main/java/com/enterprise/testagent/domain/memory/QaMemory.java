package com.enterprise.testagent.domain.memory;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 长期记忆治理记录。完整记忆正文不属于该对象，`displaySummary` 只是可降级展示的派生摘要。
 */
public record QaMemory(
        MemoryId memoryId,
        String mem0MemoryId,
        MemoryScope scope,
        String ownerUserId,
        String applicationId,
        MemoryStatus status,
        MemorySource source,
        List<QaTaskType> taskTypes,
        String displaySummary,
        double confidence,
        int distinctSessionCount,
        int distinctUserCount,
        Instant firstObservedAt,
        Instant lastObservedAt,
        Instant confirmedAt,
        String supersededByMemoryId,
        String createdByUserId,
        long version,
        String vectorSyncStatus,
        Instant createdAt,
        Instant updatedAt) {

    public QaMemory {
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(source, "source must not be null");
        taskTypes = taskTypes == null || taskTypes.isEmpty() ? List.of(QaTaskType.GENERAL) : List.copyOf(taskTypes);
        displaySummary = DomainValidation.requireText(displaySummary, "displaySummary");
        if (displaySummary.codePointCount(0, displaySummary.length()) > 500) {
            throw new IllegalArgumentException("displaySummary must not exceed 500 characters");
        }
        ownerUserId = optional(ownerUserId);
        applicationId = optional(applicationId);
        mem0MemoryId = optional(mem0MemoryId);
        supersededByMemoryId = optional(supersededByMemoryId);
        createdByUserId = DomainValidation.requireText(createdByUserId, "createdByUserId");
        vectorSyncStatus = DomainValidation.requireText(vectorSyncStatus, "vectorSyncStatus");
        Objects.requireNonNull(firstObservedAt, "firstObservedAt must not be null");
        Objects.requireNonNull(lastObservedAt, "lastObservedAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        if (distinctSessionCount < 0 || distinctUserCount < 0 || version < 0) {
            throw new IllegalArgumentException("counts and version must not be negative");
        }
        if (lastObservedAt.isBefore(firstObservedAt) || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("memory timestamps are out of order");
        }
        if (scope == MemoryScope.PERSONAL_GLOBAL && (ownerUserId == null || applicationId != null)
                || scope == MemoryScope.PERSONAL_APPLICATION && (ownerUserId == null || applicationId == null)
                || scope == MemoryScope.TEAM_APPLICATION && (ownerUserId != null || applicationId == null)) {
            throw new IllegalArgumentException("memory owner/application does not match scope");
        }
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
