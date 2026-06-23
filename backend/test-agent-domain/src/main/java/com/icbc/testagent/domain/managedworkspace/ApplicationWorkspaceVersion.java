package com.icbc.testagent.domain.managedworkspace;

import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ApplicationWorkspaceId;
import com.icbc.testagent.domain.configuration.CodeRepositoryId;
import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * 应用版本工作区，连接应用工作空间模板、Git 分支物理目录和运行态 Workspace。
 */
public record ApplicationWorkspaceVersion(
        ApplicationWorkspaceVersionId versionId,
        ApplicationWorkspaceId applicationWorkspaceId,
        ApplicationId appId,
        CodeRepositoryId repositoryId,
        String version,
        String branch,
        String repoRootPath,
        String workspaceRootPath,
        WorkspaceId runtimeWorkspaceId,
        UserId createdBy,
        ManagedWorkspaceStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public ApplicationWorkspaceVersion {
        Objects.requireNonNull(versionId, "versionId must not be null");
        Objects.requireNonNull(applicationWorkspaceId, "applicationWorkspaceId must not be null");
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        version = DomainValidation.requireText(version, "version");
        branch = DomainValidation.requireText(branch, "branch");
        repoRootPath = DomainValidation.requireText(repoRootPath, "repoRootPath");
        workspaceRootPath = DomainValidation.requireText(workspaceRootPath, "workspaceRootPath");
        Objects.requireNonNull(runtimeWorkspaceId, "runtimeWorkspaceId must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(status, "status must not be null");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
