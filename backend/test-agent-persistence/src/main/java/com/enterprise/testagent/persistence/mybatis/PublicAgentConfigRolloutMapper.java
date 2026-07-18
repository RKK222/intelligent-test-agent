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

    void upsertServerMembership(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    int decommissionServerMembership(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    int decommissionRolloutServers(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    int abandonRolloutTargets(
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
