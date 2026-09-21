package com.enterprise.testagent.domain.team;

import java.time.Instant;

/** 团队版本整组导出任务；产物路径只存在协调节点本地，不进入数据库。 */
public record TeamWorkspaceExportJob(
        String exportId,
        String actorUserId,
        String ownerUserId,
        TeamScopeMode scopeMode,
        String versionId,
        String coordinatorLinuxServerId,
        TeamWorkspaceExportStatus status,
        int totalItems,
        int completedItems,
        int succeededItems,
        int failedItems,
        int fileCount,
        long uncompressedBytes,
        Long archiveBytes,
        String archiveSha256,
        String artifactFileName,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant expiresAt,
        Instant cancelledAt) {
}
