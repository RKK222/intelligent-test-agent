package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * 应用与平台用户的成员关系；删除只标记 deletedAt，不影响其他数据。
 */
public record ApplicationMember(
        ApplicationId appId,
        UserId userId,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    public ApplicationMember {
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * 创建有效成员关系。
     */
    public static ApplicationMember active(ApplicationId appId, UserId userId, Instant now) {
        return new ApplicationMember(appId, userId, now, now, null);
    }

    /**
     * 逻辑删除成员关系。
     */
    public ApplicationMember markDeleted(Instant now) {
        return new ApplicationMember(appId, userId, createdAt, now, now);
    }

    /**
     * 判断成员关系是否已逻辑删除。
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
