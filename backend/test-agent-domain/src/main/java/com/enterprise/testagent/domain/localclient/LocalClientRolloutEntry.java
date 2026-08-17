package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** 本地客户端下载入口的用户级灰度记录；禁用后保留操作人和时间用于追溯。 */
public record LocalClientRolloutEntry(
        UserId userId,
        boolean enabled,
        UserId updatedByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public LocalClientRolloutEntry {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(updatedByUserId, "updatedByUserId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
