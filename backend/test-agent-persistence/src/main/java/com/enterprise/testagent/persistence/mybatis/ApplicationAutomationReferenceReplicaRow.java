package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** application_automation_reference_replicas 表内部行模型。 */
public record ApplicationAutomationReferenceReplicaRow(
        String appId,
        String repositoryId,
        long generation,
        String linuxServerId,
        String status,
        String currentBranch,
        String currentCommitHash,
        int retryCount,
        Instant nextRetryAt,
        String leaseToken,
        Instant leaseUntil,
        String lastError,
        Instant syncedAt,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt) {
}
