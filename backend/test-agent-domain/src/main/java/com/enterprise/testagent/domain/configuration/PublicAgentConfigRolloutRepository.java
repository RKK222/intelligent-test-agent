package com.enterprise.testagent.domain.configuration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 公共 Agent/Skill 配置发布排空状态仓储；关系型 SQL 由持久化模块 MyBatis XML 实现。
 */
public interface PublicAgentConfigRolloutRepository {

    Optional<String> findActiveRolloutId();

    /** 所有服务器同步前阻止全部用户；同步后仅阻止仍有未 dispose 旧实例的用户。 */
    Optional<String> findBlockingRolloutId(String userId);

    Optional<PublicAgentConfigRolloutPreparation> findPreparing(String linuxServerId);

    void createRollout(
            String rolloutId,
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            String initiatedByUserId,
            String initiatedLinuxServerId,
            String traceId,
            Instant now);

    boolean activateRollout(String rolloutId, String commitHash, Instant now);

    boolean recordExpectedCommit(String rolloutId, String commitHash, Instant now);

    boolean abortPreparation(String rolloutId, String reason, Instant now);

    void registerServerMembership(String linuxServerId, Instant now);

    List<String> findActiveServerMembershipIds();

    void decommissionServerMembership(String linuxServerId, Instant now);

    void addServer(String rolloutId, String linuxServerId, Instant now);

    void addTarget(PublicAgentConfigRolloutTarget target, Instant now);

    /** 返回该目标进程曾绑定过的全部工作区根目录，排空检查不得退化为默认 cwd。 */
    List<String> findTargetWorkspaceRootPaths(String targetId);

    Optional<PublicAgentConfigRolloutSyncRequest> claimPendingSync(
            String linuxServerId,
            Instant now,
            Instant leaseUntil);

    boolean renewServerSync(
            String rolloutId,
            String linuxServerId,
            String leaseToken,
            Instant leaseUntil,
            Instant now);

    boolean markServerSynced(
            String rolloutId,
            String linuxServerId,
            String leaseToken,
            Instant now);

    boolean markServerSyncRetry(
            String rolloutId,
            String linuxServerId,
            String leaseToken,
            int retryCount,
            Instant nextRetryAt,
            String errorMessage,
            Instant now);

    List<PublicAgentConfigRolloutTarget> claimTargets(
            String linuxServerId,
            Instant now,
            Instant leaseUntil,
            int limit);

    boolean markTargetRetry(
            String targetId,
            String leaseToken,
            int retryCount,
            Instant nextRetryAt,
            String errorMessage,
            Instant now);

    boolean markTargetDisposed(String targetId, String leaseToken, Instant now);

    boolean renewTargetLease(
            String targetId,
            String leaseToken,
            Instant leaseUntil,
            Instant now);

    void completeReadyRollouts(Instant now);

}
