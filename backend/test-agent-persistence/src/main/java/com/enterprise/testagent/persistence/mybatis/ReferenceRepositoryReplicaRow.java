package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** reference_repository_replicas 表内部行模型。 */
public record ReferenceRepositoryReplicaRow(
        String repositoryId,
        String linuxServerId,
        long generation,
        String status,
        String branch,
        String currentCommitHash,
        int retryCount,
        Instant nextRetryAt,
        String leaseToken,
        Instant leaseUntil,
        String lastError,
        Instant syncedAt,
        Instant createdAt,
        Instant updatedAt) {
}
