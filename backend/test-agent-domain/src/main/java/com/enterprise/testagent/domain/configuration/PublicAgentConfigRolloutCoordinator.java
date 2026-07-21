package com.enterprise.testagent.domain.configuration;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 公共 Agent/Skill 配置发布协调端口，由运行时模块负责进程快照、排空与 dispose。
 */
public interface PublicAgentConfigRolloutCoordinator {

    String prepare(
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId);

    /** 应用共享 Agent 配置推送前建立同一持久化闸门；scopeKey 为应用版本 ID。 */
    String prepareApplication(
            String versionId,
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId);

    void activate(String rolloutId, String commitHash);

    void recordExpectedCommit(String rolloutId, String commitHash);

    void abortPreparation(String rolloutId, String reason);

    Optional<PublicAgentConfigRolloutPreparation> preparing(String linuxServerId, AgentConfigRolloutScope scope);

    default Optional<PublicAgentConfigRolloutPreparation> preparing(String linuxServerId) {
        return preparing(linuxServerId, AgentConfigRolloutScope.PUBLIC);
    }

    Optional<PublicAgentConfigRolloutSyncRequest> claimPendingSync(String linuxServerId, AgentConfigRolloutScope scope);

    default Optional<PublicAgentConfigRolloutSyncRequest> claimPendingSync(String linuxServerId) {
        return claimPendingSync(linuxServerId, AgentConfigRolloutScope.PUBLIC);
    }

    boolean renewServerSync(PublicAgentConfigRolloutSyncRequest request);

    void markServerSynced(PublicAgentConfigRolloutSyncRequest request);

    /**
     * 应用级发布只登记已经同步个人 worktree 的用户进程；公共发布继续使用 {@link #markServerSynced} 覆盖全机进程。
     */
    default void markServerSyncedForUsers(
            PublicAgentConfigRolloutSyncRequest request,
            Set<String> targetUserIds) {
        markServerSynced(request);
    }

    /**
     * 完成应用共享副本同步，并把未收敛个人 worktree 转为不占用主 rollout 的持久化补偿任务。
     */
    default void markServerSyncedForUsers(
            PublicAgentConfigRolloutSyncRequest request,
            Set<String> targetUserIds,
            List<AgentConfigRolloutWorktreePending> pendingWorktrees) {
        markServerSyncedForUsers(request, targetUserIds);
    }

    Optional<AgentConfigRolloutWorktreeClaim> claimPendingApplicationWorktree(String linuxServerId);

    void markApplicationWorktreeRetry(AgentConfigRolloutWorktreeClaim claim, String reason);

    void markApplicationWorktreeSynchronized(AgentConfigRolloutWorktreeClaim claim);

    void abandonApplicationWorktree(AgentConfigRolloutWorktreeClaim claim, String reason);

    void markServerSyncRetry(PublicAgentConfigRolloutSyncRequest request, String errorMessage);

    void decommissionServer(String linuxServerId);

}
