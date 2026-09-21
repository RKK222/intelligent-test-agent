package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 团队候选用户的 MyBatis 行模型。 */
public record SystemAdminTeamUserRow(
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
