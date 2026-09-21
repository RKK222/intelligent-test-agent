package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 系统管理员团队成员列表的 MyBatis 行模型。 */
public record SystemAdminTeamMemberRow(
        String ownerUserId,
        String memberUserId,
        String addedByUserId,
        String unifiedAuthId,
        String username,
        String passwordHash,
        String organization,
        String rdDepartment,
        String department,
        String userStatus,
        Instant userCreatedAt,
        Instant userUpdatedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
