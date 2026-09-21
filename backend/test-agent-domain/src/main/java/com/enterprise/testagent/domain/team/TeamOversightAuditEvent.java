package com.enterprise.testagent.domain.team;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;

/** 团队代码只读访问审计；不允许保存文件正文、明文路径或凭据。 */
public record TeamOversightAuditEvent(
        String eventId,
        String actorUserId,
        String actorUsername,
        String targetUserId,
        String targetUsername,
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

    public TeamOversightAuditEvent {
        eventId = DomainValidation.requireText(eventId, "eventId");
        actorUserId = DomainValidation.requireText(actorUserId, "actorUserId");
        actorUsername = DomainValidation.requireText(actorUsername, "actorUsername");
        targetUserId = normalize(targetUserId);
        targetUsername = normalize(targetUsername);
        action = DomainValidation.requireText(action, "action");
        resourceType = DomainValidation.requireText(resourceType, "resourceType");
        resourceId = normalize(resourceId);
        pathDigest = digest(pathDigest, "pathDigest");
        outcome = DomainValidation.requireText(outcome, "outcome");
        errorCode = normalize(errorCode);
        traceId = DomainValidation.requireText(traceId, "traceId");
        ipAddress = normalize(ipAddress);
        userAgentDigest = digest(userAgentDigest, "userAgentDigest");
        occurredAt = DomainValidation.requireInstant(occurredAt, "occurredAt");
    }

    private static String digest(String value, String field) {
        String normalized = normalize(value);
        if (normalized != null && !normalized.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 digest");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
