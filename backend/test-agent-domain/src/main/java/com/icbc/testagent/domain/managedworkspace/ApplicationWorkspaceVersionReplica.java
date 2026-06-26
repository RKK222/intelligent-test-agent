package com.icbc.testagent.domain.managedworkspace;

import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Objects;

/**
 * 应用版本工作区在单台服务器上的物理副本，记录本机路径、运行态 Workspace 和当前 commit。
 */
public record ApplicationWorkspaceVersionReplica(
        ApplicationWorkspaceVersionReplicaId replicaId,
        ApplicationWorkspaceVersionId versionId,
        String linuxServerId,
        String repoRootPath,
        String workspaceRootPath,
        WorkspaceId runtimeWorkspaceId,
        String currentCommitHash,
        WorkspaceReplicaSyncStatus syncStatus,
        String lastError,
        Instant lastSyncedAt,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 校验副本字段；失败状态可携带脱敏错误，未完成同步时 commit 和 lastSyncedAt 允许为空。
     */
    public ApplicationWorkspaceVersionReplica {
        Objects.requireNonNull(replicaId, "replicaId must not be null");
        Objects.requireNonNull(versionId, "versionId must not be null");
        linuxServerId = DomainValidation.requireText(linuxServerId, "linuxServerId");
        repoRootPath = DomainValidation.requireText(repoRootPath, "repoRootPath");
        workspaceRootPath = DomainValidation.requireText(workspaceRootPath, "workspaceRootPath");
        Objects.requireNonNull(runtimeWorkspaceId, "runtimeWorkspaceId must not be null");
        currentCommitHash = normalizeOptional(currentCommitHash);
        Objects.requireNonNull(syncStatus, "syncStatus must not be null");
        lastError = normalizeOptional(lastError);
        traceId = DomainValidation.requireText(traceId, "traceId");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    /**
     * 返回完成同步后的副本状态。
     */
    public ApplicationWorkspaceVersionReplica ready(String commitHash, Instant syncedAt, String traceId) {
        return new ApplicationWorkspaceVersionReplica(
                replicaId,
                versionId,
                linuxServerId,
                repoRootPath,
                workspaceRootPath,
                runtimeWorkspaceId,
                commitHash,
                WorkspaceReplicaSyncStatus.READY,
                null,
                syncedAt,
                traceId,
                createdAt,
                syncedAt);
    }

    /**
     * 返回同步失败后的副本状态，错误信息应由调用方提前脱敏。
     */
    public ApplicationWorkspaceVersionReplica failed(String error, Instant failedAt, String traceId) {
        return new ApplicationWorkspaceVersionReplica(
                replicaId,
                versionId,
                linuxServerId,
                repoRootPath,
                workspaceRootPath,
                runtimeWorkspaceId,
                currentCommitHash,
                WorkspaceReplicaSyncStatus.FAILED,
                error,
                lastSyncedAt,
                traceId,
                createdAt,
                failedAt);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
