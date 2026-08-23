package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** local_client_public_capability_attempts 的 MyBatis 行模型。 */
public record LocalClientPublicCapabilityAttemptRow(
        String commandId,
        String clientInstanceId,
        String userId,
        long connectionGeneration,
        String targetCommit,
        String targetDigest,
        String status,
        String errorCode,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
