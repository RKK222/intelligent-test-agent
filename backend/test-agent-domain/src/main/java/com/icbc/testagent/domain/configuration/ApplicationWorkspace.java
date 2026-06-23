package com.icbc.testagent.domain.configuration;

import java.time.Instant;
import java.util.Objects;

/**
 * 应用级工作空间配置，与运行态 Workspace 实体无关。
 */
public record ApplicationWorkspace(
        ApplicationWorkspaceId workspaceId,
        ApplicationId appId,
        CodeRepositoryId repositoryId,
        String branch,
        String directoryPath,
        String workspaceName,
        Instant createdAt,
        Instant updatedAt) {

    public ApplicationWorkspace {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        Objects.requireNonNull(branch, "branch must not be null");
        Objects.requireNonNull(directoryPath, "directoryPath must not be null");
        Objects.requireNonNull(workspaceName, "workspaceName must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (branch.isBlank()) {
            throw new IllegalArgumentException("branch must not be blank");
        }
        if (directoryPath.isBlank()) {
            throw new IllegalArgumentException("directoryPath must not be blank");
        }
        if (workspaceName.isBlank()) {
            throw new IllegalArgumentException("workspaceName must not be blank");
        }
    }

    /**
     * 编辑展示名称，保留应用、仓库、分支和目录定位。
     */
    public ApplicationWorkspace rename(String nextName, Instant now) {
        return new ApplicationWorkspace(workspaceId, appId, repositoryId, branch, directoryPath, nextName, createdAt, now);
    }
}
