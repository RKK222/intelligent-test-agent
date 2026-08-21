package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** application_automation_references 表内部行模型。 */
public record ApplicationAutomationReferenceStateRow(
        String appId,
        String repositoryId,
        Long activeGeneration,
        Long pendingGeneration,
        long nextGeneration,
        long lockVersion,
        String status,
        String operationType,
        String traceId,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {
}
