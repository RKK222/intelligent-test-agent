package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 个人工作区搬迁 MyBatis 行模型；业务对象转换集中在仓储适配器。 */
public record PersonalWorkspaceRelocationRow(
        String relocationId,
        String personalWorkspaceId,
        String appWorkspaceVersionId,
        String userId,
        String runtimeWorkspaceId,
        String sourceLinuxServerId,
        String targetLinuxServerId,
        String branch,
        String sourceRepoRootPath,
        String sourceWorkspaceRootPath,
        String status,
        int attemptCount,
        String leaseOwner,
        Instant leaseUntil,
        String snapshotSha256,
        Long archiveSizeBytes,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {
}
