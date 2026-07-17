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

    String findActiveRolloutId();

    String findBlockingRolloutId(@Param("userId") String userId);

    PublicAgentConfigRolloutSyncRow findPendingSync(@Param("linuxServerId") String linuxServerId);

    List<String> findTargetWorkspaceRootPaths(@Param("targetId") String targetId);

    void insertRollout(
            @Param("rolloutId") String rolloutId,
            @Param("branch") String branch,
            @Param("commitHash") String commitHash,
            @Param("initiatedByUserId") String initiatedByUserId,
            @Param("traceId") String traceId,
            @Param("now") Instant now);

    void insertServer(
            @Param("rolloutId") String rolloutId,
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    void insertTarget(
            @Param("row") PublicAgentConfigRolloutTargetRow row,
            @Param("now") Instant now);

    int markServerSynced(
            @Param("rolloutId") String rolloutId,
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

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

    int completeReadyRollouts(@Param("now") Instant now);

}
