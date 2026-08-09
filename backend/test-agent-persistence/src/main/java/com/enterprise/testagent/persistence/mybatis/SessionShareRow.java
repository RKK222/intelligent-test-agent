package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** session_shares 的 MyBatis 行模型。 */
public record SessionShareRow(
        String shareId,
        String sessionId,
        String workspaceId,
        String ownerUserId,
        String status,
        Instant expiresAt,
        Long version,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Instant revokedAt) {
}
