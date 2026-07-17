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

    Optional<PublicAgentConfigRolloutSyncRequest> findPendingSync(String linuxServerId);

    void createRollout(
            String rolloutId,
            String branch,
            String commitHash,
            String initiatedByUserId,
            String traceId,
            Instant now);

    void addServer(String rolloutId, String linuxServerId, Instant now);

    void addTarget(PublicAgentConfigRolloutTarget target, Instant now);

    /** 返回该目标进程曾绑定过的全部工作区根目录，排空检查不得退化为默认 cwd。 */
    List<String> findTargetWorkspaceRootPaths(String targetId);

    void markServerSynced(String rolloutId, String linuxServerId, Instant now);

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

    void completeReadyRollouts(Instant now);

}
