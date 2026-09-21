package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 团队版本人员及其可选个人 worktree 查询行。 */
public record TeamWorkspaceContributionRow(
        String userId,
        String unifiedAuthId,
        String username,
        String passwordHash,
        String organization,
        String rdDepartment,
        String department,
        String userStatus,
        Instant userCreatedAt,
        Instant userUpdatedAt,
        String membershipState,
        String personalWorkspaceId,
        String versionId,
        String appId,
        String applicationWorkspaceId,
        String workspaceName,
        String branch,
        String repoRootPath,
        String workspaceRootPath,
        String runtimeWorkspaceId,
        String baseCommit,
        String workspaceStatus,
        Instant workspaceCreatedAt,
        Instant workspaceUpdatedAt,
        String linuxServerId) {
}
