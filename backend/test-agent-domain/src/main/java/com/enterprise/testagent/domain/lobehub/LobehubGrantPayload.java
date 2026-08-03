package com.enterprise.testagent.domain.lobehub;

import java.time.Instant;
import java.util.Objects;

/** LobeHub 服务端访问模型网关时使用的用户委托载荷。 */
public record LobehubGrantPayload(
        String userId,
        String clientId,
        String scope,
        Instant expiresAt) {

    /** 委托必须同时绑定用户、客户端、scope 和过期时间。 */
    public LobehubGrantPayload {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (userId.isBlank() || clientId.isBlank() || scope.isBlank()) {
            throw new IllegalArgumentException("grant identity must not be blank");
        }
    }
}
