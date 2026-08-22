package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** application_automation_reference_generations 表内部行模型。 */
public record ApplicationAutomationReferenceGenerationRow(
        String appId,
        String repositoryId,
        long generation,
        String branch,
        String directoryPath,
        String description,
        String referenceAlias,
        boolean mergeEnabled,
        String targetCommitHash,
        String status,
        String operationType,
        String operatedByUserId,
        String operationId,
        String traceId,
        String lastError,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt) {
}
