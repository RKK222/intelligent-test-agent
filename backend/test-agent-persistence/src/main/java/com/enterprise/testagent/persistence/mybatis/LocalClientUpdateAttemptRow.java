package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** local_client_update_attempts 的 MyBatis 行模型。 */
public record LocalClientUpdateAttemptRow(
        String commandId,
        String rolloutId,
        String clientInstanceId,
        String userId,
        long connectionGeneration,
        long policyRevision,
        String currentVersion,
        String targetVersion,
        String direction,
        String status,
        String releaseDigest,
        String errorCode,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
