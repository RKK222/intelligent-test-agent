package com.enterprise.testagent.domain.user;

import java.time.Instant;
import java.util.Objects;

/**
 * 用户聚合根，表示平台用户的核心领域模型。
 */
public record User(
        UserId userId,
        String unifiedAuthId,
        String username,
        String passwordHash,
        String organization,
        String rdDepartment,
        String department,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 校验用户聚合根的不变量。
     */
    public User {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(unifiedAuthId, "unifiedAuthId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (unifiedAuthId.isBlank()) {
            throw new IllegalArgumentException("unifiedAuthId must not be blank");
        }
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
    }

    /**
     * 检查用户是否可以登录。
     */
    public boolean canLogin() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * 创建新用户的静态工厂方法。
     */
    public static User createNew(
            String userId,
            String unifiedAuthId,
            String username,
            String passwordHash,
            String organization,
            String rdDepartment,
            String department) {
        Instant now = Instant.now();
        return new User(
                new UserId(userId),
                unifiedAuthId,
                username,
                passwordHash,
                organization,
                rdDepartment,
                department,
                UserStatus.ACTIVE,
                now,
                now);
    }
}
