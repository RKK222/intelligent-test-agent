package com.icbc.testagent.domain.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * 认证主体，表示经过认证的用户在 Token 中的存储信息。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthPrincipal(
        String token,
        UserId userId,
        String username,
        String unifiedAuthId,
        Instant issuedAt,
        Instant expiresAt) {

    /**
     * 校验认证主体的不变量。
     */
    public AuthPrincipal {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(unifiedAuthId, "unifiedAuthId must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (unifiedAuthId.isBlank()) {
            throw new IllegalArgumentException("unifiedAuthId must not be blank");
        }
    }

    /**
     * 检查 Token 是否已过期。
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
