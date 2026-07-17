package com.enterprise.testagent.domain.configuration;

import java.util.Optional;

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

    void activate(String rolloutId, String commitHash);

    void recordExpectedCommit(String rolloutId, String commitHash);

    void abortPreparation(String rolloutId, String reason);

    Optional<PublicAgentConfigRolloutPreparation> preparing(String linuxServerId);

    Optional<PublicAgentConfigRolloutSyncRequest> claimPendingSync(String linuxServerId);

    boolean renewServerSync(PublicAgentConfigRolloutSyncRequest request);

    void markServerSynced(PublicAgentConfigRolloutSyncRequest request);

    void markServerSyncRetry(PublicAgentConfigRolloutSyncRequest request, String errorMessage);

    void decommissionServer(String linuxServerId);

}
