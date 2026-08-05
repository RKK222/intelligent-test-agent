package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 排查授权 MyBatis 行模型。 */
public record SupportAccessGrantRow(
        String grantId,
        String actorUserId,
        String actorUsername,
        String incidentId,
        String reason,
        String sessionDigest,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokeReason,
        String traceId) {
}
