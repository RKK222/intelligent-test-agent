package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * 个人工作区，基于某个应用版本工作区派生，最终同样映射为运行态 Workspace。
 */
public record PersonalWorkspace(
        PersonalWorkspaceId personalWorkspaceId,
        ApplicationWorkspaceVersionId versionId,
        ApplicationId appId,
        ApplicationWorkspaceId applicationWorkspaceId,
        UserId userId,
        String workspaceName,
        String branch,
        String repoRootPath,
        String workspaceRootPath,
        WorkspaceId runtimeWorkspaceId,
        String baseCommit,
        ManagedWorkspaceStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public PersonalWorkspace {
        Objects.requireNonNull(personalWorkspaceId, "personalWorkspaceId must not be null");
        Objects.requireNonNull(versionId, "versionId must not be null");
        Objects.requireNonNull(appId, "appId must not be null");
        Objects.requireNonNull(applicationWorkspaceId, "applicationWorkspaceId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        workspaceName = DomainValidation.requireText(workspaceName, "workspaceName");
        branch = DomainValidation.requireText(branch, "branch");
        repoRootPath = DomainValidation.requireText(repoRootPath, "repoRootPath");
        workspaceRootPath = DomainValidation.requireText(workspaceRootPath, "workspaceRootPath");
        Objects.requireNonNull(runtimeWorkspaceId, "runtimeWorkspaceId must not be null");
        baseCommit = baseCommit == null ? "" : baseCommit.trim();
        Objects.requireNonNull(status, "status must not be null");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
