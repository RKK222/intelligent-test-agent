package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 团队可见应用版本查询行。 */
public record TeamWorkspaceVersionRow(
        String versionId,
        String applicationWorkspaceId,
        String appId,
        String repositoryId,
        String version,
        String branch,
        String repoRootPath,
        String workspaceRootPath,
        String runtimeWorkspaceId,
        String createdByUserId,
        String status,
        String targetCommitHash,
        Instant targetCommitUpdatedAt,
        Instant createdAt,
        Instant updatedAt) {
}
