package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * application_workspaces 表行模型，仅在 persistence MyBatis 映射内部使用。
 */
public record ApplicationWorkspaceRow(
        String workspaceId,
        String appId,
        String repositoryId,
        String branch,
        String directoryPath,
        String workspaceName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
