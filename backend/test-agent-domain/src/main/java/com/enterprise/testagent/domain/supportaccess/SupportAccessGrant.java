package com.enterprise.testagent.domain.supportaccess;

import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;

/**
 * 超级管理员问题排查授权；只保存不可逆会话摘要，不保存平台 Token 或授权 Token 明文。
 */
public record SupportAccessGrant(
        String grantId,
        UserId actorUserId,
        String actorUsername,
        String incidentId,
        String reason,
        String sessionDigest,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        String revokeReason,
        String traceId) {

    public SupportAccessGrant {
        grantId = DomainValidation.requireText(grantId, "grantId");
        actorUsername = DomainValidation.requireText(actorUsername, "actorUsername");
        incidentId = DomainValidation.requireText(incidentId, "incidentId");
        reason = DomainValidation.requireText(reason, "reason");
        sessionDigest = requireDigest(sessionDigest, "sessionDigest");
        issuedAt = DomainValidation.requireInstant(issuedAt, "issuedAt");
        expiresAt = DomainValidation.requireInstant(expiresAt, "expiresAt");
        revokeReason = normalizeOptional(revokeReason);
        traceId = DomainValidation.requireText(traceId, "traceId");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        if (revokedAt != null && revokedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("revokedAt must not be before issuedAt");
        }
    }

    /** 当前时刻授权仍可使用。 */
    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    private static String requireDigest(String value, String field) {
        String normalized = DomainValidation.requireText(value, field);
        if (!normalized.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 digest");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
