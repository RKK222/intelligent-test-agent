package com.enterprise.testagent.domain.configuration;

import java.util.Optional;

/**
 * 公共 Agent/Skill 配置发布协调端口，由运行时模块负责进程快照、排空与 dispose。
 */
public interface PublicAgentConfigRolloutCoordinator {

    String begin(String branch, String commitHash, String localLinuxServerId, String initiatedByUserId, String traceId);

    void markServerSynced(String rolloutId, String linuxServerId, String traceId);

    Optional<PublicAgentConfigRolloutSyncRequest> pendingSync(String linuxServerId);

}
