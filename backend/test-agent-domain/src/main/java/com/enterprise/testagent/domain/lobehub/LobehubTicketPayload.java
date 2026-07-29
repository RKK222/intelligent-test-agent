package com.enterprise.testagent.domain.lobehub;

import java.time.Instant;
import java.util.Objects;

/** LobeHub 一次性登录票据在 Redis 中对应的最小业务载荷。 */
public record LobehubTicketPayload(String userId, Instant expiresAt) {

    /** 票据只绑定平台用户，其他身份属性在兑换时重新读取。 */
    public LobehubTicketPayload {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
    }
}
