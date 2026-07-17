package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutRepository;
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
    public Optional<PublicAgentConfigRolloutSyncRequest> findPendingSync(String linuxServerId) {
        return Optional.ofNullable(mapper.findPendingSync(linuxServerId))
                .map(row -> new PublicAgentConfigRolloutSyncRequest(
                        row.rolloutId(), row.branch(), row.commitHash(), row.initiatedByUserId(), row.traceId()));
    }

    @Override
    public void createRollout(
            String rolloutId,
            String branch,
            String commitHash,
            String initiatedByUserId,
            String traceId,
            Instant now) {
        mapper.insertRollout(rolloutId, branch, commitHash, initiatedByUserId, traceId, now);
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
    public void markServerSynced(String rolloutId, String linuxServerId, Instant now) {
        mapper.markServerSynced(rolloutId, linuxServerId, now);
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
                            row.port(), row.baseUrl(), row.retryCount(), leaseUntil, leaseToken, row.traceId());
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
    public void completeReadyRollouts(Instant now) {
        mapper.completeReadyRollouts(now);
    }

    private PublicAgentConfigRolloutTargetRow toRow(PublicAgentConfigRolloutTarget target) {
        return new PublicAgentConfigRolloutTargetRow(
                target.targetId(), target.rolloutId(), target.userId(), target.linuxServerId(), target.containerId(),
                target.port(), target.baseUrl(), target.retryCount(), target.leaseUntil(), target.leaseToken(),
                target.traceId());
    }
}
