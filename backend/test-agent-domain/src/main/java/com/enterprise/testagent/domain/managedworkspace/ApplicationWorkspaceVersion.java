package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
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
        String targetCommitHash,
        Instant targetCommitUpdatedAt,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 兼容旧创建路径；目标 commit 可在 clone/pull/push 完成后再回填。
     */
    public ApplicationWorkspaceVersion(
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
        this(
                versionId,
                applicationWorkspaceId,
                appId,
                repositoryId,
                version,
                branch,
                repoRootPath,
                workspaceRootPath,
                runtimeWorkspaceId,
                createdBy,
                status,
                null,
                null,
                createdAt,
                updatedAt);
    }

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
        targetCommitHash = normalizeOptional(targetCommitHash);
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    /**
     * 返回更新目标 commit 后的版本副本；该 commit 是所有服务器副本要追平的目标。
     */
    public ApplicationWorkspaceVersion withTargetCommit(String targetCommitHash, Instant updatedAt) {
        return new ApplicationWorkspaceVersion(
                versionId,
                applicationWorkspaceId,
                appId,
                repositoryId,
                version,
                branch,
                repoRootPath,
                workspaceRootPath,
                runtimeWorkspaceId,
                createdBy,
                status,
                targetCommitHash,
                updatedAt,
                createdAt,
                updatedAt);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
