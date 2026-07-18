package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.configuration.AgentConfigRolloutScope;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutRepository;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutPreparation;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutTarget;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutSyncRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公共 Agent/Skill 配置发布排空仓储的 MyBatis 实现。
 */
@Repository
public class MyBatisPublicAgentConfigRolloutRepository implements PublicAgentConfigRolloutRepository {

    private final PublicAgentConfigRolloutMapper mapper;

    public MyBatisPublicAgentConfigRolloutRepository(PublicAgentConfigRolloutMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<String> findActiveRolloutId() {
        return Optional.ofNullable(mapper.findActiveRolloutId());
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
    public void registerServerMembership(String linuxServerId, Instant now) {
        mapper.upsertServerMembership(linuxServerId, now);
    }

    @Override
    public List<String> findActiveServerMembershipIds() {
        return mapper.findActiveServerMembershipIds();
    }

    @Override
    @Transactional
    public void decommissionServerMembership(String linuxServerId, Instant now) {
        mapper.decommissionServerMembership(linuxServerId, now);
        mapper.decommissionRolloutServers(linuxServerId, now);
        mapper.abandonRolloutTargets(linuxServerId, now);
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
                            row.branch(), row.commitHash(), row.initiatedByUserId(), row.traceId(),
                            row.retryCount(), leaseUntil, leaseToken));
                });
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
                    return new PublicAgentConfigRolloutTarget(
                            row.targetId(), row.rolloutId(), row.userId(), row.linuxServerId(), row.containerId(),
                            row.port(), row.processPid(), row.processStartedAt(), row.baseUrl(), row.retryCount(),
                            leaseUntil, leaseToken, row.traceId());
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
                target.targetId(), target.rolloutId(), target.userId(), target.linuxServerId(), target.containerId(),
                target.port(), target.processPid(), target.processStartedAt(), target.baseUrl(), target.retryCount(),
                target.leaseUntil(), target.leaseToken(), target.traceId());
    }
}
