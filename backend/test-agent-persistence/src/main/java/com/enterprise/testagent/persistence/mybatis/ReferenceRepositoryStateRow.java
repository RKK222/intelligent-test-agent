package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** reference_repository_states 表内部行模型。 */
public record ReferenceRepositoryStateRow(
        String repositoryId,
        String branch,
        String targetCommitHash,
        long generation,
        String status,
        String operationType,
        String credentialUserId,
        String traceId,
        String lastError,
        Instant initializedAt,
        Instant createdAt,
        Instant updatedAt) {
}
