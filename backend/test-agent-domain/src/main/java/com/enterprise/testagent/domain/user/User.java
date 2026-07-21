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
     * 保留用户业务 ID、认证号、密码和已有业务关联，仅用外部身份源刷新展示姓名与部门信息。
     *
     * <p>organization 不在当前 TCDS 响应中，因此沿用原值；空白部门统一转为空值，避免写入无意义空串。
     */
    public User refreshExternalProfile(String refreshedUsername, String refreshedRdDepartment, String refreshedDepartment) {
        String normalizedUsername = Objects.requireNonNull(refreshedUsername, "refreshedUsername must not be null").trim();
        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("refreshedUsername must not be blank");
        }
        return new User(
                userId,
                unifiedAuthId,
                normalizedUsername,
                passwordHash,
                organization,
                normalizeOptional(refreshedRdDepartment),
                normalizeOptional(refreshedDepartment),
                status,
                createdAt,
                Instant.now());
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

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
