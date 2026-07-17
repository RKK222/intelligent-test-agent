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
    public Optional<PublicAgentConfigRolloutSyncRequest> findPendingSync(String linuxServerId) {
        return Optional.ofNullable(mapper.findPendingSync(linuxServerId))
                .map(row -> new PublicAgentConfigRolloutSyncRequest(
                        row.rolloutId(), row.branch(), row.commitHash(), row.traceId()));
    }

    @Override
    public void createRollout(String rolloutId, String branch, String commitHash, String traceId, Instant now) {
        mapper.insertRollout(rolloutId, branch, commitHash, traceId, now);
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
        rows.forEach(row -> mapper.markTargetProcessing(row.targetId(), leaseUntil, now));
        return rows.stream()
                .map(row -> new PublicAgentConfigRolloutTarget(
                        row.targetId(), row.rolloutId(), row.linuxServerId(), row.containerId(), row.port(),
                        row.baseUrl(), row.retryCount(), leaseUntil))
                .toList();
    }

    @Override
    public void markTargetRetry(String targetId, int retryCount, Instant nextRetryAt, String errorMessage, Instant now) {
        mapper.markTargetRetry(targetId, retryCount, nextRetryAt, errorMessage, now);
    }

    @Override
    public void markTargetDisposed(String targetId, Instant now) {
        mapper.markTargetDisposed(targetId, now);
    }

    @Override
    public void completeReadyRollouts(Instant now) {
        mapper.completeReadyRollouts(now);
    }

    @Override
    public void markFailed(String rolloutId, String reason, Instant now) {
        mapper.markFailed(rolloutId, reason, now);
    }

    private PublicAgentConfigRolloutTargetRow toRow(PublicAgentConfigRolloutTarget target) {
        return new PublicAgentConfigRolloutTargetRow(
                target.targetId(), target.rolloutId(), target.linuxServerId(), target.containerId(), target.port(),
                target.baseUrl(), target.retryCount(), target.leaseUntil());
    }
}
