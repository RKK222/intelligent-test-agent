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
            boolean discardSharedRuntimeChanges,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId);

    /**
     * 用已确认的远端修正提交原子替换一个仍在排空的公共发布。
     * 旧任务及其租约在同一事务内封存，新任务直接进入 DRAINING，消息门禁没有可见空窗。
     */
    String supersede(
            String activeRolloutId,
            String branch,
            String commitHash,
            String previousCommitHash,
            boolean discardSharedRuntimeChanges,
            String reason,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId);

    /** 存量发布调用默认不放弃共享运行副本修改。 */
    default String prepare(
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId) {
        return prepare(
                branch,
                expectedCommitHash,
                previousCommitHash,
                false,
                localLinuxServerId,
                initiatedByUserId,
                traceId);
    }

    /** 应用共享 Agent 配置推送前建立同一持久化闸门；scopeKey 为应用版本 ID。 */
    String prepareApplication(
            String versionId,
            String branch,
            String expectedCommitHash,
            String previousCommitHash,
            String localLinuxServerId,
            String initiatedByUserId,
            String traceId);

    /**
     * 个人拉取已经合入应用 Agent 配置后，仅为当前用户登记持久化运行态重载。
     *
     * @return 已登记的 rolloutId；当前用户进程未运行时为空，下次启动直接读取最新磁盘配置
     */
    Optional<String> schedulePersonalApplicationReload(
            String personalWorkspaceId,
            String branch,
            String commitHash,
            String localLinuxServerId,
            String userId,
            String traceId);

    void activate(String rolloutId, String commitHash);

    void recordExpectedCommit(String rolloutId, String commitHash);

    void abortPreparation(String rolloutId, String reason);

    Optional<PublicAgentConfigRolloutPreparation> preparing(String linuxServerId, AgentConfigRolloutScope scope);

    default Optional<PublicAgentConfigRolloutPreparation> preparing(String linuxServerId) {
        return preparing(linuxServerId, AgentConfigRolloutScope.PUBLIC);
    }

    /** 超级管理员页面读取最近一次公共全局 rollout 及各服务器状态。 */
    Optional<PublicAgentConfigRolloutStatus> latestPublicRolloutStatus();

    Optional<PublicAgentConfigRolloutSyncRequest> claimPendingSync(String linuxServerId, AgentConfigRolloutScope scope);

    default Optional<PublicAgentConfigRolloutSyncRequest> claimPendingSync(String linuxServerId) {
        return claimPendingSync(linuxServerId, AgentConfigRolloutScope.PUBLIC);
    }

    boolean renewServerSync(PublicAgentConfigRolloutSyncRequest request);

    void markServerSynced(PublicAgentConfigRolloutSyncRequest request);

    /** 公共共享副本已同步；未合入的个人 worktree 转入独立补偿，不占用主 rollout。 */
    default void markPublicServerSynced(
            PublicAgentConfigRolloutSyncRequest request,
            List<PublicAgentConfigWorktreePending> pendingWorktrees) {
        markServerSynced(request);
    }

    Optional<PublicAgentConfigWorktreeClaim> claimPendingPublicWorktree(String linuxServerId);

    void markPublicWorktreeRetry(PublicAgentConfigWorktreeClaim claim, String reason);

    void markPublicWorktreeSynchronized(PublicAgentConfigWorktreeClaim claim);

    void abandonPublicWorktree(PublicAgentConfigWorktreeClaim claim, String reason);

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
