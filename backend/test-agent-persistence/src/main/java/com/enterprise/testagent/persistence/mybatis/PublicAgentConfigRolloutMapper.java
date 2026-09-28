package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 公共 Agent/Skill 配置发布排空 MyBatis mapper；SQL 统一维护在 XML。
 */
@Mapper
public interface PublicAgentConfigRolloutMapper {

    String findActiveRolloutId(
            @Param("scope") String scope,
            @Param("scopeKey") String scopeKey);

    PublicAgentConfigRolloutStatusRow findLatestRolloutStatus(
            @Param("scope") String scope,
            @Param("scopeKey") String scopeKey);

    List<PublicAgentConfigRolloutStatusRow> findRecentRolloutStatuses(
            @Param("scope") String scope,
            @Param("limit") int limit);

    List<PublicAgentConfigRolloutServerStatusRow> findRolloutServerStatuses(
            @Param("rolloutId") String rolloutId);

    List<PublicAgentConfigRolloutTargetStatusRow> findPendingRolloutTargets(
            @Param("rolloutId") String rolloutId,
            @Param("limitPerServer") int limitPerServer);

    List<String> findRolloutServerIds(@Param("rolloutId") String rolloutId);

    String findBlockingRolloutId(@Param("userId") String userId);

    boolean hasAwaitingPublicSync(@Param("rolloutId") String rolloutId);

    String lockPublicRolloutForSyncResume(@Param("rolloutId") String rolloutId);

    void authorizePublicSyncDiscard(@Param("rolloutId") String rolloutId,
            @Param("discard") boolean discard, @Param("now") Instant now);

    int resumePublicServerSyncs(@Param("rolloutId") String rolloutId, @Param("now") Instant now);

    int markServerSyncAwaitingAction(@Param("rolloutId") String rolloutId,
            @Param("linuxServerId") String linuxServerId, @Param("leaseToken") String leaseToken,
            @Param("errorMessage") String errorMessage, @Param("now") Instant now);

    PublicAgentConfigRolloutPreparationRow findPreparing(
            @Param("linuxServerId") String linuxServerId,
            @Param("scope") String scope);

    List<String> findActiveServerMembershipIds();

    List<String> findTargetWorkspaceRootPaths(@Param("targetId") String targetId);

    void insertRollout(
            @Param("rolloutId") String rolloutId,
            @Param("scope") String scope,
            @Param("scopeKey") String scopeKey,
            @Param("branch") String branch,
            @Param("expectedCommitHash") String expectedCommitHash,
            @Param("previousCommitHash") String previousCommitHash,
            @Param("discardSharedRuntimeChanges") boolean discardSharedRuntimeChanges,
            @Param("initiatedByUserId") String initiatedByUserId,
            @Param("initiatedLinuxServerId") String initiatedLinuxServerId,
            @Param("traceId") String traceId,
            @Param("now") Instant now);

    int activateRollout(
            @Param("rolloutId") String rolloutId,
            @Param("commitHash") String commitHash,
            @Param("now") Instant now);

    int recordExpectedCommit(
            @Param("rolloutId") String rolloutId,
            @Param("commitHash") String commitHash,
            @Param("now") Instant now);

    int abortPreparation(
            @Param("rolloutId") String rolloutId,
            @Param("reason") String reason,
            @Param("now") Instant now);

    int markPublicRolloutSuperseded(
            @Param("activeRolloutId") String activeRolloutId,
            @Param("reason") String reason,
            @Param("now") Instant now);

    void abandonSupersededRolloutServers(
            @Param("activeRolloutId") String activeRolloutId,
            @Param("now") Instant now);

    void abandonSupersededRolloutTargets(
            @Param("activeRolloutId") String activeRolloutId,
            @Param("now") Instant now);

    void abandonSupersededRolloutWorktrees(
            @Param("activeRolloutId") String activeRolloutId,
            @Param("now") Instant now);

    void abandonSupersededPublicRolloutWorktrees(
            @Param("activeRolloutId") String activeRolloutId,
            @Param("now") Instant now);

    void insertSupersedingPublicRollout(
            @Param("rolloutId") String rolloutId,
            @Param("supersedesRolloutId") String supersedesRolloutId,
            @Param("branch") String branch,
            @Param("commitHash") String commitHash,
            @Param("previousCommitHash") String previousCommitHash,
            @Param("discardSharedRuntimeChanges") boolean discardSharedRuntimeChanges,
            @Param("initiatedByUserId") String initiatedByUserId,
            @Param("initiatedLinuxServerId") String initiatedLinuxServerId,
            @Param("traceId") String traceId,
            @Param("reason") String reason,
            @Param("now") Instant now);

    int linkSupersededPublicRollout(
            @Param("activeRolloutId") String activeRolloutId,
            @Param("replacementRolloutId") String replacementRolloutId,
            @Param("now") Instant now);

    void upsertServerMembership(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    int decommissionServerMembership(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    int decommissionRolloutServers(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    List<PublicAgentConfigRolloutTargetRow> findPendingTargetsByServer(
            @Param("linuxServerId") String linuxServerId);

    int abandonRolloutTargets(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    int abandonRolloutWorktrees(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    int abandonPublicRolloutWorktrees(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    void insertServer(
            @Param("rolloutId") String rolloutId,
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    void insertTarget(
            @Param("row") PublicAgentConfigRolloutTargetRow row,
            @Param("now") Instant now);

    List<PublicAgentConfigRolloutSyncRow> findClaimableServerSyncs(
            @Param("linuxServerId") String linuxServerId,
            @Param("scope") String scope,
            @Param("now") Instant now,
            @Param("limit") int limit);

    int markServerSyncProcessing(
            @Param("rolloutId") String rolloutId,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int renewServerSync(
            @Param("rolloutId") String rolloutId,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int markServerSynced(
            @Param("rolloutId") String rolloutId,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("now") Instant now);

    int markServerSyncRetry(
            @Param("rolloutId") String rolloutId,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("errorMessage") String errorMessage,
            @Param("now") Instant now);

    void upsertPendingPublicWorktrees(
            @Param("rolloutId") String rolloutId,
            @Param("linuxServerId") String linuxServerId,
            @Param("targetCommit") String targetCommit,
            @Param("traceId") String traceId,
            @Param("rows") List<com.enterprise.testagent.domain.configuration.PublicAgentConfigWorktreePending> rows,
            @Param("now") Instant now);

    List<PublicAgentConfigWorktreeRow> findClaimablePublicWorktrees(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now,
            @Param("limit") int limit);

    int markPublicWorktreeProcessing(
            @Param("rolloutId") String rolloutId,
            @Param("worktreeId") String worktreeId,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int markPublicWorktreeRetry(
            @Param("rolloutId") String rolloutId,
            @Param("worktreeId") String worktreeId,
            @Param("leaseToken") String leaseToken,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("reason") String reason,
            @Param("now") Instant now);

    int markPublicWorktreeSynchronized(
            @Param("rolloutId") String rolloutId,
            @Param("worktreeId") String worktreeId,
            @Param("leaseToken") String leaseToken,
            @Param("now") Instant now);

    int abandonPublicWorktree(
            @Param("rolloutId") String rolloutId,
            @Param("worktreeId") String worktreeId,
            @Param("leaseToken") String leaseToken,
            @Param("reason") String reason,
            @Param("now") Instant now);

    void upsertPendingApplicationWorktrees(
            @Param("rolloutId") String rolloutId,
            @Param("linuxServerId") String linuxServerId,
            @Param("targetCommit") String targetCommit,
            @Param("traceId") String traceId,
            @Param("rows") List<com.enterprise.testagent.domain.configuration.AgentConfigRolloutWorktreePending> rows,
            @Param("now") Instant now);

    List<AgentConfigRolloutWorktreeRow> findClaimableApplicationWorktrees(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now,
            @Param("limit") int limit);

    int markApplicationWorktreeProcessing(
            @Param("rolloutId") String rolloutId,
            @Param("personalWorkspaceId") String personalWorkspaceId,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int markApplicationWorktreeRetry(
            @Param("rolloutId") String rolloutId,
            @Param("personalWorkspaceId") String personalWorkspaceId,
            @Param("leaseToken") String leaseToken,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("reason") String reason,
            @Param("now") Instant now);

    int markApplicationWorktreeSynchronized(
            @Param("rolloutId") String rolloutId,
            @Param("personalWorkspaceId") String personalWorkspaceId,
            @Param("leaseToken") String leaseToken,
            @Param("now") Instant now);

    int abandonApplicationWorktree(
            @Param("rolloutId") String rolloutId,
            @Param("personalWorkspaceId") String personalWorkspaceId,
            @Param("leaseToken") String leaseToken,
            @Param("reason") String reason,
            @Param("now") Instant now);

    int countIncompleteApplicationWorktrees(
            @Param("rolloutId") String rolloutId,
            @Param("linuxServerId") String linuxServerId,
            @Param("userId") String userId);

    List<PublicAgentConfigRolloutTargetRow> findClaimableTargets(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now,
            @Param("limit") int limit);

    int markTargetProcessing(
            @Param("targetId") String targetId,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int markTargetRetry(
            @Param("targetId") String targetId,
            @Param("leaseToken") String leaseToken,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("errorMessage") String errorMessage,
            @Param("now") Instant now);

    int markTargetDisposed(
            @Param("targetId") String targetId,
            @Param("leaseToken") String leaseToken,
            @Param("now") Instant now);

    int renewTargetLease(
            @Param("targetId") String targetId,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int completeReadyRollouts(@Param("now") Instant now);

}
