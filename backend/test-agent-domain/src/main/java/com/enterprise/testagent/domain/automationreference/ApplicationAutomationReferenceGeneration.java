package com.enterprise.testagent.domain.automationreference;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 自动化引用不可变配置代次；别名、分支、目录、描述和目标提交在代次创建后不再修改。 */
public record ApplicationAutomationReferenceGeneration(
        ApplicationId appId,
        CodeRepositoryId repositoryId,
        long generation,
        String branch,
        String directoryPath,
        String description,
        String referenceAlias,
        boolean merge,
        String targetCommitHash,
        AutomationReferenceGenerationStatus status,
        AutomationReferenceOperationType operationType,
        UserId operatedByUserId,
        String operationId,
        String traceId,
        String lastError,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt) {

    public ApplicationAutomationReferenceGeneration {
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(operationType, "operationType must not be null");
        Objects.requireNonNull(operatedByUserId, "operatedByUserId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (generation < 1L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        branch = requireText(branch, "branch");
        directoryPath = normalizeDirectory(directoryPath);
        description = requireText(description, "description");
        referenceAlias = requireText(referenceAlias, "referenceAlias");
        targetCommitHash = requireText(targetCommitHash, "targetCommitHash");
        operationId = requireText(operationId, "operationId");
        traceId = requireText(traceId, "traceId");
        lastError = normalize(lastError);
        if (merge) {
            throw new IllegalArgumentException("automation reference merge must be false");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeDirectory(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        return ".".equals(normalized) ? "" : normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
