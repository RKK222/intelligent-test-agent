package com.enterprise.testagent.domain.configuration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 公共 Agent/Skill 配置发布排空状态仓储；关系型 SQL 由持久化模块 MyBatis XML 实现。
 */
public interface PublicAgentConfigRolloutRepository {

    Optional<String> findActiveRolloutId();

    Optional<PublicAgentConfigRolloutSyncRequest> findPendingSync(String linuxServerId);

    void createRollout(String rolloutId, String branch, String commitHash, String traceId, Instant now);

    void addServer(String rolloutId, String linuxServerId, Instant now);

    void addTarget(PublicAgentConfigRolloutTarget target, Instant now);

    void markServerSynced(String rolloutId, String linuxServerId, Instant now);

    List<PublicAgentConfigRolloutTarget> claimTargets(
            String linuxServerId,
            Instant now,
            Instant leaseUntil,
            int limit);

    void markTargetRetry(String targetId, int retryCount, Instant nextRetryAt, String errorMessage, Instant now);

    void markTargetDisposed(String targetId, Instant now);

    void completeReadyRollouts(Instant now);

    void markFailed(String rolloutId, String reason, Instant now);
}
