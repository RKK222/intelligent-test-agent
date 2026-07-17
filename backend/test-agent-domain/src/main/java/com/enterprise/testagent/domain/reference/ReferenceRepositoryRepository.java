package com.enterprise.testagent.domain.reference;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 引用资产库状态与副本任务仓储端口；实现必须用 generation 和 leaseToken 做 fencing。 */
public interface ReferenceRepositoryRepository {

    Optional<ReferenceRepositoryState> findState(CodeRepositoryId repositoryId);

    /** 按 repositoryId 稳定游标分页，afterExclusive 为 null 时从首行开始。 */
    List<ReferenceRepositoryState> findStatesAfter(CodeRepositoryId afterExclusive, int limit);

    List<ReferenceRepositoryReplica> findReplicas(CodeRepositoryId repositoryId);

    /** 仅在状态行不存在时原子写入首次分支和目标提交；竞争失败返回 empty。 */
    Optional<ReferenceRepositoryState> initializeIfAbsent(ReferenceRepositoryState state);

    /** 仅当数据库仍为 expectedGeneration 且处于终态时推进 generation；竞争失败返回 empty。 */
    Optional<ReferenceRepositoryState> advanceGenerationIfCurrent(
            long expectedGeneration,
            ReferenceRepositoryState nextState);

    void upsertTargets(
            CodeRepositoryId repositoryId,
            long generation,
            String branch,
            Collection<LinuxServerId> linuxServerIds,
            Instant now);

    /** 当前 generation 中不在线的非终态副本转为 DEFERRED；空在线集合表示全部离线。 */
    int deferOfflineReplicas(
            CodeRepositoryId repositoryId,
            long generation,
            Collection<LinuxServerId> liveServerIds,
            Instant now);

    Optional<ReferenceRepositoryReplica> claimReplica(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            Instant leaseUntil,
            Instant now);

    /** 仅当前 token 仍持有未过期租约时续期；返回 false 表示 worker 必须停止共享目录写入。 */
    boolean renewLease(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            Instant leaseUntil,
            Instant now);

    List<ReferenceRepositoryReplica> findClaimableReplicas(
            LinuxServerId linuxServerId,
            Instant now,
            int limit);

    boolean markReady(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            String branch,
            String commitHash,
            Instant syncedAt,
            Instant now);

    boolean markRetry(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            int retryCount,
            Instant nextRetryAt,
            String lastError,
            Instant now);

    boolean markBlocked(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String leaseToken,
            String lastError,
            Instant now);

    boolean updateOverallStatus(
            CodeRepositoryId repositoryId,
            long generation,
            ReferenceRepositoryStatus status,
            String lastError,
            Instant now);
}
