package com.enterprise.testagent.domain.configuration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 公共 Agent/Skill 配置发布排空状态仓储；关系型 SQL 由持久化模块 MyBatis XML 实现。
 */
public interface PublicAgentConfigRolloutRepository {

    /** 查询同一配置作用域内的活动发布；公共范围 scopeKey 为空，应用范围按版本 ID 隔离。 */
    Optional<String> findActiveRolloutId(AgentConfigRolloutScope scope, String scopeKey);

    default Optional<String> findActiveRolloutId() {
        return findActiveRolloutId(AgentConfigRolloutScope.PUBLIC, null);
    }

    /** 查询指定作用域最近一次 rollout 主状态；服务器明细通过独立查询组合。 */
    Optional<PublicAgentConfigRolloutStatus> findLatestRolloutStatus(
            AgentConfigRolloutScope scope,
            String scopeKey);

    /** 查询一次 rollout 的全服务器 Git 同步、排空计数和最近错误。 */
    List<PublicAgentConfigRolloutServerStatus> findRolloutServerStatuses(String rolloutId);

    /** 查询 rollout 中尚未退役的服务器，用于纠错发布完整继承原发布覆盖范围。 */
    List<String> findRolloutServerIds(String rolloutId);

    /** 所有服务器同步前阻止全部用户；同步后仅阻止仍有未 dispose 旧实例的用户。 */
    Optional<String> findBlockingRolloutId(String userId);

    Optional<PublicAgentConfigRolloutPreparation> findPreparing(String linuxServerId, AgentConfigRolloutScope scope);

    void createRollout(
            String rolloutId,
            AgentConfigRolloutScope scope,
            String scopeKey,
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            boolean discardSharedRuntimeChanges,
            String initiatedByUserId,
            String initiatedLinuxServerId,
            String traceId,
            Instant now);

    /** 存量应用与个人 rollout 不授权清理公共运行副本。 */
    default void createRollout(
            String rolloutId,
            AgentConfigRolloutScope scope,
            String scopeKey,
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            String initiatedByUserId,
            String initiatedLinuxServerId,
            String traceId,
            Instant now) {
        createRollout(
                rolloutId,
                scope,
                scopeKey,
                branch,
                expectedCommitHash,
                previousCommitHash,
                false,
                initiatedByUserId,
                initiatedLinuxServerId,
                traceId,
                now);
    }

    boolean activateRollout(String rolloutId, String commitHash, Instant now);

    boolean recordExpectedCommit(String rolloutId, String commitHash, Instant now);

    boolean abortPreparation(String rolloutId, String reason, Instant now);

    /**
     * CAS 封存指定 DRAINING 公共发布、清除其全部在途租约并创建已激活的新发布。
     * 实现必须保证旧发布退出活动态和新发布进入活动态在同一数据库事务提交。
     */
    boolean supersedePublicRollout(
            String activeRolloutId,
            String replacementRolloutId,
            String branch,
            String commitHash,
            String previousCommitHash,
            boolean discardSharedRuntimeChanges,
            String initiatedByUserId,
            String initiatedLinuxServerId,
            String traceId,
            String reason,
            List<String> serverIds,
            Instant now);

    void registerServerMembership(String linuxServerId, Instant now);

    List<String> findActiveServerMembershipIds();

    /** 查询服务器退役前仍会被弃用的进程目标，供通知状态与数据库终态同步推进。 */
    List<PublicAgentConfigRolloutTarget> findPendingTargetsByServer(String linuxServerId);

    void decommissionServerMembership(String linuxServerId, Instant now);

    void addServer(String rolloutId, String linuxServerId, Instant now);

    void addTarget(PublicAgentConfigRolloutTarget target, Instant now);

    /** 返回该目标进程曾绑定过的全部工作区根目录，排空检查不得退化为默认 cwd。 */
    List<String> findTargetWorkspaceRootPaths(String targetId);

    Optional<PublicAgentConfigRolloutSyncRequest> claimPendingSync(
            String linuxServerId,
            AgentConfigRolloutScope scope,
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

    void savePendingPublicWorktrees(
            String rolloutId,
            String linuxServerId,
            String targetCommit,
            String traceId,
            List<PublicAgentConfigWorktreePending> pendingWorktrees,
            Instant now);

    Optional<PublicAgentConfigWorktreeClaim> claimPendingPublicWorktree(
            String linuxServerId,
            Instant now,
            Instant leaseUntil);

    boolean markPublicWorktreeRetry(
            PublicAgentConfigWorktreeClaim claim,
            int retryCount,
            Instant nextRetryAt,
            String reason,
            Instant now);

    boolean markPublicWorktreeSynchronized(
            PublicAgentConfigWorktreeClaim claim,
            Instant now);

    boolean abandonPublicWorktree(
            PublicAgentConfigWorktreeClaim claim,
            String reason,
            Instant now);

    void savePendingApplicationWorktrees(
            String rolloutId,
            String linuxServerId,
            String targetCommit,
            String traceId,
            List<AgentConfigRolloutWorktreePending> pendingWorktrees,
            Instant now);

    Optional<AgentConfigRolloutWorktreeClaim> claimPendingApplicationWorktree(
            String linuxServerId,
            Instant now,
            Instant leaseUntil);

    boolean markApplicationWorktreeRetry(
            AgentConfigRolloutWorktreeClaim claim,
            int retryCount,
            Instant nextRetryAt,
            String reason,
            Instant now);

    boolean markApplicationWorktreeSynchronized(
            AgentConfigRolloutWorktreeClaim claim,
            Instant now);

    boolean abandonApplicationWorktree(
            AgentConfigRolloutWorktreeClaim claim,
            String reason,
            Instant now);

    boolean hasIncompleteApplicationWorktrees(
            String rolloutId,
            String linuxServerId,
            String userId);

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
