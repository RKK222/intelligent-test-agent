package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** local_client_update_rollouts 的 MyBatis 行模型。 */
public record LocalClientRolloutRow(
        String rolloutId,
        String rolloutScope,
        String requestedUserId,
        String status,
        String createdBy,
        Instant createdAt,
        Instant completedAt) {
}
