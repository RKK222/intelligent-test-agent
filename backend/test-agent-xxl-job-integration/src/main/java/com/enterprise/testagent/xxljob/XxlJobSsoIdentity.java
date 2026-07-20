package com.enterprise.testagent.xxljob;

import java.time.Instant;

/** 一次性票据承载的最小身份快照，不包含平台原始 Token。 */
public record XxlJobSsoIdentity(
        String platformUserId,
        String displayName,
        String sessionDigest,
        Instant sessionExpiresAt) {

    public XxlJobSsoIdentity {
        platformUserId = requireText(platformUserId, "platformUserId");
        displayName = requireText(displayName, "displayName");
        sessionDigest = requireText(sessionDigest, "sessionDigest");
        if (!sessionDigest.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("sessionDigest must be SHA-256 hex");
        }
        if (sessionExpiresAt == null) {
            throw new IllegalArgumentException("sessionExpiresAt must not be null");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
