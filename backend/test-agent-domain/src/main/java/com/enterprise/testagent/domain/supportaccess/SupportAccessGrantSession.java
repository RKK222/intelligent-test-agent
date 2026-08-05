package com.enterprise.testagent.domain.supportaccess;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;

/**
 * Redis 中的短期排查授权上下文；tokenDigest 与平台 sessionDigest 均为不可逆摘要。
 */
public record SupportAccessGrantSession(
        String grantId,
        String actorUserId,
        String sessionDigest,
        String grantTokenDigest,
        Instant expiresAt) {

    public SupportAccessGrantSession {
        grantId = DomainValidation.requireText(grantId, "grantId");
        actorUserId = DomainValidation.requireText(actorUserId, "actorUserId");
        sessionDigest = requireDigest(sessionDigest, "sessionDigest");
        grantTokenDigest = requireDigest(grantTokenDigest, "grantTokenDigest");
        expiresAt = DomainValidation.requireInstant(expiresAt, "expiresAt");
    }

    private static String requireDigest(String value, String field) {
        String normalized = DomainValidation.requireText(value, field);
        if (!normalized.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 digest");
        }
        return normalized;
    }
}
