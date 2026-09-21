package com.enterprise.testagent.domain.team;

import java.time.Instant;

/** 团队导出的成员/worktree 处理结果。 */
public record TeamWorkspaceExportItem(
        String exportItemId,
        String exportId,
        String userId,
        String personalWorkspaceId,
        String sourceLinuxServerId,
        String status,
        int fileCount,
        long uncompressedBytes,
        Long shardBytes,
        String shardSha256,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {
}
