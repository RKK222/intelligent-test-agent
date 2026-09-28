package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.configuration.AgentConfigRolloutScope;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutWorktreeClaim;
import com.enterprise.testagent.domain.configuration.AgentConfigRolloutWorktreePending;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutRepository;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutServerStatus;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutStatus;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutPreparation;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutTarget;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutTargetStatus;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigWorktreeClaim;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigWorktreePending;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutSyncRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公共 Agent/Skill 配置发布排空仓储的 MyBatis 实现。
 */
@Repository
public class MyBatisPublicAgentConfigRolloutRepository implements PublicAgentConfigRolloutRepository {

    /** 页面诊断明细按服务器显式限量，聚合计数仍保持完整。 */
    private static final int PENDING_TARGET_DETAIL_LIMIT_PER_SERVER = 200;

    private final PublicAgentConfigRolloutMapper mapper;

    public MyBatisPublicAgentConfigRolloutRepository(PublicAgentConfigRolloutMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<String> findActiveRolloutId(AgentConfigRolloutScope scope, String scopeKey) {
        return Optional.ofNullable(mapper.findActiveRolloutId(scope.name(), scopeKey));
    }

    @Override
    public Optional<PublicAgentConfigRolloutStatus> findLatestRolloutStatus(
            AgentConfigRolloutScope scope,
            String scopeKey) {
        return Optional.ofNullable(mapper.findLatestRolloutStatus(scope.name(), scopeKey))
                .map(row -> new PublicAgentConfigRolloutStatus(
                        row.rolloutId(),
                        row.status(),
                        row.branch(),
                        row.commitHash(),
                        row.failureReason(),
                        row.supersedesRolloutId(),
                        row.supersededByRolloutId(),
                        row.supersedeReason(),
                        row.createdAt(),
                        row.updatedAt(),
                        row.completedAt(),
                        List.of(),
                        AgentConfigRolloutScope.valueOf(row.configScope()),
                        row.scopeKey()));
    }

    @Override
    public List<PublicAgentConfigRolloutStatus> findRecentRolloutStatuses(
            AgentConfigRolloutScope scope,
            int limit) {
        return mapper.findRecentRolloutStatuses(scope.name(), Math.max(1, Math.min(limit, 50))).stream()
                .map(row -> new PublicAgentConfigRolloutStatus(
                        row.rolloutId(),
                        row.status(),
                        row.branch(),
                        row.commitHash(),
                        row.failureReason(),
                        row.supersedesRolloutId(),
                        row.supersededByRolloutId(),
                        row.supersedeReason(),
                        row.createdAt(),
                        row.updatedAt(),
                        row.completedAt(),
                        List.of(),
                        AgentConfigRolloutScope.valueOf(row.configScope()),
                        row.scopeKey()))
                .toList();
    }

    @Override
    public List<PublicAgentConfigRolloutServerStatus> findRolloutServerStatuses(String rolloutId) {
        Map<String, List<PublicAgentConfigRolloutTargetStatus>> pendingTargetsByServer = mapper
                .findPendingRolloutTargets(rolloutId, PENDING_TARGET_DETAIL_LIMIT_PER_SERVER)
                .stream()
                .map(row -> new PublicAgentConfigRolloutTargetStatus(
                        row.targetId(),
                        row.userId(),
                        row.username(),
                        row.linuxServerId(),
                        row.containerId(),
                        row.port(),
                        row.processPid(),
                        row.processStartedAt(),
                        row.status(),
                        row.retryCount(),
                        row.nextRetryAt(),
                        row.lastError(),
                        row.forceStop(),
                        row.updatedAt()))
                .collect(Collectors.groupingBy(PublicAgentConfigRolloutTargetStatus::linuxServerId));
        return mapper.findRolloutServerStatuses(rolloutId).stream()
                .map(row -> new PublicAgentConfigRolloutServerStatus(
                        row.linuxServerId(),
                        row.syncStatus(),
                        row.retryCount(),
                        row.targetTotal(),
                        row.targetPending(),
                        row.targetDisposed(),
                        row.targetAbandoned(),
                        row.worktreeTotal(),
                        row.worktreePending(),
                        row.worktreeSynced(),
                        row.lastError(),
                        row.syncedAt(),
                        row.updatedAt(),
                        pendingTargetsByServer.getOrDefault(row.linuxServerId(), List.of())))
                .toList();
    }

    @Override
    public List<String> findRolloutServerIds(String rolloutId) {
        return mapper.findRolloutServerIds(rolloutId);
    }

    @Override
    public Optional<String> findBlockingRolloutId(String userId) {
        return Optional.ofNullable(mapper.findBlockingRolloutId(userId));
    }

    @Override
    public Optional<PublicAgentConfigRolloutPreparation> findPreparing(
            String linuxServerId,
            AgentConfigRolloutScope scope) {
        return Optional.ofNullable(mapper.findPreparing(linuxServerId, scope.name()))
                .map(row -> new PublicAgentConfigRolloutPreparation(
                        row.rolloutId(), AgentConfigRolloutScope.valueOf(row.scope()), row.scopeKey(),
                        row.branch(), row.expectedCommitHash(), row.previousCommitHash(),
                        row.initiatedByUserId(),
                        row.initiatedLinuxServerId(), row.traceId(), row.createdAt()));
    }

    @Override
    public void createRollout(
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
            Instant now) {
        mapper.insertRollout(
                rolloutId,
                scope.name(),
                scopeKey,
                branch,
                expectedCommitHash,
                previousCommitHash,
                discardSharedRuntimeChanges,
                initiatedByUserId,
                initiatedLinuxServerId,
                traceId,
                now);
    }

    @Override
    public boolean activateRollout(String rolloutId, String commitHash, Instant now) {
        return mapper.activateRollout(rolloutId, commitHash, now) == 1;
    }

    @Override
    public boolean recordExpectedCommit(String rolloutId, String commitHash, Instant now) {
        return mapper.recordExpectedCommit(rolloutId, commitHash, now) == 1;
    }

    @Override
    public boolean abortPreparation(String rolloutId, String reason, Instant now) {
        return mapper.abortPreparation(rolloutId, reason, now) == 1;
    }

    @Override
    public boolean hasAwaitingPublicSync(String rolloutId) {
        return mapper.hasAwaitingPublicSync(rolloutId);
    }

    @Override
    @Transactional
    public boolean resumePublicSync(String rolloutId, boolean discardSharedRuntimeChanges, Instant now) {
        // 与 supersede 竞争同一主记录锁；只恢复仍处于当前发布中的暂停服务器。
        if (mapper.lockPublicRolloutForSyncResume(rolloutId) == null) {
            return false;
        }
        if (mapper.resumePublicServerSyncs(rolloutId, now) == 0) {
            return false;
        }
        mapper.authorizePublicSyncDiscard(rolloutId, discardSharedRuntimeChanges, now);
        return true;
    }

    @Override
    public boolean markServerSyncAwaitingAction(
            String rolloutId, String linuxServerId, String leaseToken, String errorMessage, Instant now) {
        return mapper.markServerSyncAwaitingAction(rolloutId, linuxServerId, leaseToken, errorMessage, now) == 1;
    }

    @Override
    @Transactional
    public boolean supersedePublicRollout(
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
            Instant now) {
        if (mapper.markPublicRolloutSuperseded(activeRolloutId, reason, now) != 1) {
            return false;
        }
        // 先封存旧租约，再建立新活动记录；整个方法受同一事务保护，对其它请求没有门禁空窗。
        mapper.abandonSupersededRolloutServers(activeRolloutId, now);
        mapper.abandonSupersededRolloutTargets(activeRolloutId, now);
        mapper.abandonSupersededRolloutWorktrees(activeRolloutId, now);
        mapper.abandonSupersededPublicRolloutWorktrees(activeRolloutId, now);
        mapper.insertSupersedingPublicRollout(
                replacementRolloutId,
                activeRolloutId,
                branch,
                commitHash,
                previousCommitHash,
                discardSharedRuntimeChanges,
                initiatedByUserId,
                initiatedLinuxServerId,
                traceId,
                reason,
                now);
        serverIds.forEach(serverId -> mapper.insertServer(replacementRolloutId, serverId, now));
        if (mapper.linkSupersededPublicRollout(activeRolloutId, replacementRolloutId, now) != 1) {
            throw new IllegalStateException("Failed to link superseded public Agent config rollout");
        }
        return true;
    }

    @Override
    public void registerServerMembership(String linuxServerId, Instant now) {
        mapper.upsertServerMembership(linuxServerId, now);
    }

    @Override
    public List<String> findActiveServerMembershipIds() {
        return mapper.findActiveServerMembershipIds();
    }

    @Override
    public List<PublicAgentConfigRolloutTarget> findPendingTargetsByServer(String linuxServerId) {
        return mapper.findPendingTargetsByServer(linuxServerId).stream()
                .map(this::toTarget)
                .toList();
    }

    @Override
    @Transactional
    public void decommissionServerMembership(String linuxServerId, Instant now) {
        mapper.decommissionServerMembership(linuxServerId, now);
        mapper.decommissionRolloutServers(linuxServerId, now);
        mapper.abandonRolloutTargets(linuxServerId, now);
        mapper.abandonRolloutWorktrees(linuxServerId, now);
        mapper.abandonPublicRolloutWorktrees(linuxServerId, now);
        mapper.completeReadyRollouts(now);
    }

    @Override
    public void addServer(String rolloutId, String linuxServerId, Instant now) {
        mapper.insertServer(rolloutId, linuxServerId, now);
    }

    @Override
    public void addTarget(PublicAgentConfigRolloutTarget target, Instant now) {
        mapper.insertTarget(toRow(target), now);
    }

    @Override
    public List<String> findTargetWorkspaceRootPaths(String targetId) {
        return mapper.findTargetWorkspaceRootPaths(targetId);
    }

    @Override
    @Transactional
    public Optional<PublicAgentConfigRolloutSyncRequest> claimPendingSync(
            String linuxServerId,
            AgentConfigRolloutScope scope,
            Instant now,
            Instant leaseUntil) {
        return mapper.findClaimableServerSyncs(linuxServerId, scope.name(), now, 1).stream()
                .findFirst()
                .flatMap(row -> {
                    String leaseToken = com.enterprise.testagent.common.id.RuntimeIdGenerator
                            .publicAgentConfigRolloutLeaseToken();
                    int updated = mapper.markServerSyncProcessing(
                            row.rolloutId(), linuxServerId, leaseToken, leaseUntil, now);
                    if (updated != 1) {
                        return Optional.empty();
                    }
                    return Optional.of(new PublicAgentConfigRolloutSyncRequest(
                            row.rolloutId(), AgentConfigRolloutScope.valueOf(row.scope()), row.scopeKey(),
                            row.branch(), row.commitHash(), row.discardSharedRuntimeChanges(),
                            row.initiatedByUserId(), row.traceId(),
                            row.retryCount(), leaseUntil, leaseToken));
                });
    }

    @Override
    public void savePendingPublicWorktrees(
            String rolloutId,
            String linuxServerId,
            String targetCommit,
            String traceId,
            List<PublicAgentConfigWorktreePending> pendingWorktrees,
            Instant now) {
        if (pendingWorktrees == null || pendingWorktrees.isEmpty()) {
            return;
        }
        mapper.upsertPendingPublicWorktrees(
                rolloutId, linuxServerId, targetCommit, traceId, pendingWorktrees, now);
    }

    @Override
    @Transactional
    public Optional<PublicAgentConfigWorktreeClaim> claimPendingPublicWorktree(
            String linuxServerId,
            Instant now,
            Instant leaseUntil) {
        return mapper.findClaimablePublicWorktrees(linuxServerId, now, 1).stream()
                .findFirst()
                .flatMap(row -> {
                    String leaseToken = com.enterprise.testagent.common.id.RuntimeIdGenerator
                            .publicAgentConfigRolloutLeaseToken();
                    int updated = mapper.markPublicWorktreeProcessing(
                            row.rolloutId(), row.worktreeId(), leaseToken, leaseUntil, now);
                    if (updated != 1) {
                        return Optional.empty();
                    }
                    return Optional.of(new PublicAgentConfigWorktreeClaim(
                            row.rolloutId(), row.worktreeId(), row.userId(), row.linuxServerId(),
                            row.targetCommit(), row.traceId(), row.retryCount(), leaseUntil, leaseToken));
                });
    }

    @Override
    public boolean markPublicWorktreeRetry(
            PublicAgentConfigWorktreeClaim claim,
            int retryCount,
            Instant nextRetryAt,
            String reason,
            Instant now) {
        return mapper.markPublicWorktreeRetry(
                claim.rolloutId(), claim.worktreeId(), claim.leaseToken(), retryCount, nextRetryAt, reason, now) == 1;
    }

    @Override
    public boolean markPublicWorktreeSynchronized(PublicAgentConfigWorktreeClaim claim, Instant now) {
        return mapper.markPublicWorktreeSynchronized(
                claim.rolloutId(), claim.worktreeId(), claim.leaseToken(), now) == 1;
    }

    @Override
    public boolean abandonPublicWorktree(
            PublicAgentConfigWorktreeClaim claim,
            String reason,
            Instant now) {
        return mapper.abandonPublicWorktree(
                claim.rolloutId(), claim.worktreeId(), claim.leaseToken(), reason, now) == 1;
    }

    @Override
    public boolean renewServerSync(
            String rolloutId,
            String linuxServerId,
            String leaseToken,
            Instant leaseUntil,
            Instant now) {
        return mapper.renewServerSync(rolloutId, linuxServerId, leaseToken, leaseUntil, now) == 1;
    }

    @Override
    public boolean markServerSynced(
            String rolloutId,
            String linuxServerId,
            String leaseToken,
            Instant now) {
        return mapper.markServerSynced(rolloutId, linuxServerId, leaseToken, now) == 1;
    }

    @Override
    public boolean markServerSyncRetry(
            String rolloutId,
            String linuxServerId,
            String leaseToken,
            int retryCount,
            Instant nextRetryAt,
            String errorMessage,
            Instant now) {
        return mapper.markServerSyncRetry(
                rolloutId,
                linuxServerId,
                leaseToken,
                retryCount,
                nextRetryAt,
                errorMessage,
                now) == 1;
    }

    @Override
    public void savePendingApplicationWorktrees(
            String rolloutId,
            String linuxServerId,
            String targetCommit,
            String traceId,
            List<AgentConfigRolloutWorktreePending> pendingWorktrees,
            Instant now) {
        if (pendingWorktrees == null || pendingWorktrees.isEmpty()) {
            return;
        }
        mapper.upsertPendingApplicationWorktrees(
                rolloutId, linuxServerId, targetCommit, traceId, pendingWorktrees, now);
    }

    @Override
    @Transactional
    public Optional<AgentConfigRolloutWorktreeClaim> claimPendingApplicationWorktree(
            String linuxServerId,
            Instant now,
            Instant leaseUntil) {
        return mapper.findClaimableApplicationWorktrees(linuxServerId, now, 1).stream()
                .findFirst()
                .flatMap(row -> {
                    String leaseToken = com.enterprise.testagent.common.id.RuntimeIdGenerator
                            .publicAgentConfigRolloutLeaseToken();
                    int updated = mapper.markApplicationWorktreeProcessing(
                            row.rolloutId(), row.personalWorkspaceId(), leaseToken, leaseUntil, now);
                    if (updated != 1) {
                        return Optional.empty();
                    }
                    return Optional.of(new AgentConfigRolloutWorktreeClaim(
                            row.rolloutId(),
                            row.versionId(),
                            row.personalWorkspaceId(),
                            row.userId(),
                            row.linuxServerId(),
                            row.targetCommit(),
                            row.traceId(),
                            row.retryCount(),
                            leaseUntil,
                            leaseToken));
                });
    }

    @Override
    public boolean markApplicationWorktreeRetry(
            AgentConfigRolloutWorktreeClaim claim,
            int retryCount,
            Instant nextRetryAt,
            String reason,
            Instant now) {
        return mapper.markApplicationWorktreeRetry(
                claim.rolloutId(),
                claim.personalWorkspaceId(),
                claim.leaseToken(),
                retryCount,
                nextRetryAt,
                reason,
                now) == 1;
    }

    @Override
    public boolean markApplicationWorktreeSynchronized(
            AgentConfigRolloutWorktreeClaim claim,
            Instant now) {
        return mapper.markApplicationWorktreeSynchronized(
                claim.rolloutId(), claim.personalWorkspaceId(), claim.leaseToken(), now) == 1;
    }

    @Override
    public boolean abandonApplicationWorktree(
            AgentConfigRolloutWorktreeClaim claim,
            String reason,
            Instant now) {
        return mapper.abandonApplicationWorktree(
                claim.rolloutId(), claim.personalWorkspaceId(), claim.leaseToken(), reason, now) == 1;
    }

    @Override
    public boolean hasIncompleteApplicationWorktrees(
            String rolloutId,
            String linuxServerId,
            String userId) {
        return mapper.countIncompleteApplicationWorktrees(rolloutId, linuxServerId, userId) > 0;
    }

    /**
     * linuxServerId 限定本机实例，PostgreSQL 行锁和租约保证本机多 Java 进程不会重复处理。
     */
    @Override
    @Transactional
    public List<PublicAgentConfigRolloutTarget> claimTargets(
            String linuxServerId,
            Instant now,
            Instant leaseUntil,
            int limit) {
        List<PublicAgentConfigRolloutTargetRow> rows = mapper.findClaimableTargets(linuxServerId, now, limit);
        return rows.stream()
                .map(row -> {
                    String leaseToken = com.enterprise.testagent.common.id.RuntimeIdGenerator
                            .publicAgentConfigRolloutLeaseToken();
                    int updated = mapper.markTargetProcessing(row.targetId(), leaseToken, leaseUntil, now);
                    if (updated != 1) {
                        return null;
                    }
                    return toTarget(new PublicAgentConfigRolloutTargetRow(
                            row.targetId(), row.rolloutId(), row.configScope(), row.userId(),
                            row.linuxServerId(), row.containerId(), row.port(), row.processPid(),
                            row.processStartedAt(), row.baseUrl(), row.retryCount(), leaseUntil,
                            leaseToken, row.traceId(), row.forceStop(),
                            row.previousCommitHash(), row.commitHash(), row.scopeKey()));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public boolean markTargetRetry(
            String targetId,
            String leaseToken,
            int retryCount,
            Instant nextRetryAt,
            String errorMessage,
            Instant now) {
        return mapper.markTargetRetry(targetId, leaseToken, retryCount, nextRetryAt, errorMessage, now) == 1;
    }

    @Override
    public boolean markTargetDisposed(String targetId, String leaseToken, Instant now) {
        return mapper.markTargetDisposed(targetId, leaseToken, now) == 1;
    }

    @Override
    public boolean renewTargetLease(
            String targetId,
            String leaseToken,
            Instant leaseUntil,
            Instant now) {
        return mapper.renewTargetLease(targetId, leaseToken, leaseUntil, now) == 1;
    }

    @Override
    public void completeReadyRollouts(Instant now) {
        mapper.completeReadyRollouts(now);
    }

    private PublicAgentConfigRolloutTargetRow toRow(PublicAgentConfigRolloutTarget target) {
        return new PublicAgentConfigRolloutTargetRow(
                target.targetId(), target.rolloutId(), target.configScope().name(),
                target.userId(), target.linuxServerId(), target.containerId(),
                target.port(), target.processPid(), target.processStartedAt(), target.baseUrl(), target.retryCount(),
                target.leaseUntil(), target.leaseToken(), target.traceId(), target.forceStop(),
                target.previousCommitHash(), target.commitHash(), target.scopeKey());
    }

    private PublicAgentConfigRolloutTarget toTarget(PublicAgentConfigRolloutTargetRow row) {
        return new PublicAgentConfigRolloutTarget(
                row.targetId(), row.rolloutId(), AgentConfigRolloutScope.valueOf(row.configScope()),
                row.userId(), row.linuxServerId(), row.containerId(), row.port(), row.processPid(),
                row.processStartedAt(), row.baseUrl(), row.retryCount(), row.leaseUntil(),
                row.leaseToken(), row.traceId(), row.forceStop(),
                row.previousCommitHash(), row.commitHash(), row.scopeKey());
    }
}
