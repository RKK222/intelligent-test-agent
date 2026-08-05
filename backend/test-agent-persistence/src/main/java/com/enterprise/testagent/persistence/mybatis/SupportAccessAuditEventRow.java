package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 排查审计 MyBatis 行模型。 */
public record SupportAccessAuditEventRow(
        String eventId,
        String grantId,
        String actorUserId,
        String actorUsername,
        String targetUserId,
        String targetUsername,
        String incidentId,
        String reason,
        String action,
        String resourceType,
        String resourceId,
        String pathDigest,
        String outcome,
        String errorCode,
        String traceId,
        String ipAddress,
        String userAgentDigest,
        Instant occurredAt) {
}
