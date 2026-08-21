package com.enterprise.testagent.domain.automationreference;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import java.time.Instant;
import java.util.Objects;

/** `(appId, repositoryId)` 唯一自动化引用状态；配置内容保存在不可变 generation 中。 */
public record ApplicationAutomationReferenceState(
        ApplicationId appId,
        CodeRepositoryId repositoryId,
        Long activeGeneration,
        Long pendingGeneration,
        long nextGeneration,
        long lockVersion,
        ReferenceRepositoryStatus status,
        AutomationReferenceOperationType operationType,
        String traceId,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {

    public ApplicationAutomationReferenceState {
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(operationType, "operationType must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (nextGeneration < 1L || lockVersion < 0L) {
            throw new IllegalArgumentException("nextGeneration must be positive and lockVersion must not be negative");
        }
        traceId = traceId.trim();
        lastError = normalize(lastError);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
