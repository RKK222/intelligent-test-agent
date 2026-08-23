package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** local_client_public_capability_states 的 MyBatis 行模型。 */
public record LocalClientPublicCapabilityStateRow(
        String clientInstanceId,
        String activeCommit,
        String activeDigest,
        String pendingCommit,
        String pendingDigest,
        String status,
        String errorCode,
        Instant reportedAt,
        Instant updatedAt) {
}
