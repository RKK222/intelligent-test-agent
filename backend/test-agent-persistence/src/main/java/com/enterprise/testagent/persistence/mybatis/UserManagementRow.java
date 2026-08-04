package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 用户管理分页查询的 users 表行模型，仅在 MyBatis 查询实现内部使用。
 */
public record UserManagementRow(
        String userId,
        String unifiedAuthId,
        String username,
        String passwordHash,
        String organization,
        String rdDepartment,
        String department,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
