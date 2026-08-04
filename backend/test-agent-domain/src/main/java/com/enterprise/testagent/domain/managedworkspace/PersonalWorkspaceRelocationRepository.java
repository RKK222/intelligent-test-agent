package com.enterprise.testagent.domain.managedworkspace;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 个人工作区跨服务器搬迁持久化端口。
 *
 * <p>目标切换方法必须在一个关系型数据库事务内同时更新 Workspace、个人工作区和搬迁状态。</p>
 */
public interface PersonalWorkspaceRelocationRepository {

    List<PersonalWorkspaceRelocationCandidate> findMismatches(String sourceLinuxServerId, int limit);

    void discover(
            PersonalWorkspaceRelocationCandidate candidate,
            String relocationId,
            String traceId,
            Instant now);

    List<PersonalWorkspaceRelocation> findClaimable(String sourceLinuxServerId, Instant now, int limit);

    Optional<PersonalWorkspaceRelocation> claim(
            String relocationId,
            String leaseOwner,
            Instant leaseUntil,
            Instant now);

    Optional<PersonalWorkspaceRelocation> findByRelocationId(String relocationId);

    boolean markTransferring(
            String relocationId,
            String leaseOwner,
            String snapshotSha256,
            long archiveSizeBytes,
            Instant now);

    boolean markApplying(String relocationId, String snapshotSha256, long archiveSizeBytes, Instant now);

    /**
     * 完成目标端切换。返回 false 表示服务器绑定、运行状态或租约事实已变化，调用方不得清理源目录。
     */
    boolean completeTarget(
            String relocationId,
            String sourceLinuxServerId,
            String targetLinuxServerId,
            String targetRepoRootPath,
            String targetWorkspaceRootPath,
            String baseCommit,
            String traceId,
            Instant now);

    boolean reschedule(
            String relocationId,
            String leaseOwner,
            int attemptCount,
            Instant nextRetryAt,
            String safeErrorCode,
            String safeErrorMessage,
            Instant now);

    boolean completeCleanup(String relocationId, String leaseOwner, Instant now);
}
