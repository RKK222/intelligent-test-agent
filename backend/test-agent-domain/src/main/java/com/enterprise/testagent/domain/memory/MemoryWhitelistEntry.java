package com.enterprise.testagent.domain.memory;

import java.time.Instant;
import java.util.Objects;

/** 用户级记忆灰度白名单；移除后保留既有记忆但停止学习、检索并隐藏页面入口。 */
public record MemoryWhitelistEntry(
        String userId,
        boolean enabled,
        String updatedByUserId,
        Instant createdAt,
        Instant updatedAt) {
    public MemoryWhitelistEntry {
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
