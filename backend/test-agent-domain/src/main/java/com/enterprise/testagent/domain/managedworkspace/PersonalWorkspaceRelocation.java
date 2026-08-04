package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/** 可租约认领、可跨节点恢复的个人工作区搬迁记录及其源端快照事实。 */
public record PersonalWorkspaceRelocation(
        String relocationId,
        PersonalWorkspaceId personalWorkspaceId,
        ApplicationWorkspaceVersionId versionId,
        UserId userId,
        WorkspaceId runtimeWorkspaceId,
        String sourceLinuxServerId,
        String targetLinuxServerId,
        String branch,
        String sourceRepoRootPath,
        String sourceWorkspaceRootPath,
        PersonalWorkspaceRelocationStatus status,
        int attemptCount,
        String leaseOwner,
        Instant leaseUntil,
        String snapshotSha256,
        Long archiveSizeBytes,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {

    public PersonalWorkspaceRelocation {
        relocationId = requireText(relocationId, "relocationId");
        Objects.requireNonNull(personalWorkspaceId, "personalWorkspaceId must not be null");
        Objects.requireNonNull(versionId, "versionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(runtimeWorkspaceId, "runtimeWorkspaceId must not be null");
        sourceLinuxServerId = requireText(sourceLinuxServerId, "sourceLinuxServerId");
        targetLinuxServerId = requireText(targetLinuxServerId, "targetLinuxServerId");
        branch = requireText(branch, "branch");
        sourceRepoRootPath = requireText(sourceRepoRootPath, "sourceRepoRootPath");
        sourceWorkspaceRootPath = requireText(sourceWorkspaceRootPath, "sourceWorkspaceRootPath");
        Objects.requireNonNull(status, "status must not be null");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        if (archiveSizeBytes != null && archiveSizeBytes < 0) {
            throw new IllegalArgumentException("archiveSizeBytes must not be negative");
        }
        traceId = requireText(traceId, "traceId");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public boolean cleanupPending() {
        return status == PersonalWorkspaceRelocationStatus.CLEANUP_PENDING;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
