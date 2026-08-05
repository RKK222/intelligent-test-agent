package com.enterprise.testagent.domain.supportaccess;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;

/**
 * 问题排查审计事件。内容正文、文件路径和任何 Token 都不得进入该对象。
 */
public record SupportAccessAuditEvent(
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

    public SupportAccessAuditEvent {
        eventId = DomainValidation.requireText(eventId, "eventId");
        grantId = normalizeOptional(grantId);
        actorUserId = normalizeOptional(actorUserId);
        actorUsername = DomainValidation.requireText(actorUsername, "actorUsername");
        targetUserId = normalizeOptional(targetUserId);
        targetUsername = normalizeOptional(targetUsername);
        incidentId = normalizeOptional(incidentId);
        reason = normalizeOptional(reason);
        action = DomainValidation.requireText(action, "action");
        resourceType = DomainValidation.requireText(resourceType, "resourceType");
        resourceId = normalizeOptional(resourceId);
        pathDigest = normalizeDigest(pathDigest, "pathDigest");
        outcome = DomainValidation.requireText(outcome, "outcome");
        errorCode = normalizeOptional(errorCode);
        traceId = DomainValidation.requireText(traceId, "traceId");
        ipAddress = normalizeOptional(ipAddress);
        userAgentDigest = normalizeDigest(userAgentDigest, "userAgentDigest");
        occurredAt = DomainValidation.requireInstant(occurredAt, "occurredAt");
    }

    private static String normalizeDigest(String value, String field) {
        String normalized = normalizeOptional(value);
        if (normalized != null && !normalized.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 digest");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
