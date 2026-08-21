package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 应用级自动化引用 MyBatis mapper；关系型 SQL 统一维护在 XML。 */
@Mapper
public interface ApplicationAutomationReferenceMapper {

    int insertStateIfAbsent(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("traceId") String traceId,
            @Param("now") Instant now);

    ApplicationAutomationReferenceStateRow findState(
            @Param("appId") String appId, @Param("repositoryId") String repositoryId);

    ApplicationAutomationReferenceGenerationRow findGeneration(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation);

    ApplicationAutomationReferenceGenerationRow findByOperationId(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("operationId") String operationId);

    List<ApplicationAutomationReferenceGenerationRow> findRecoverableGenerations(@Param("limit") int limit);

    List<ApplicationAutomationReferenceReplicaRow> findReplicas(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation);

    List<String> findKnownServerIds(
            @Param("appId") String appId, @Param("repositoryId") String repositoryId);

    int reserveState(
            @Param("row") ApplicationAutomationReferenceGenerationRow row,
            @Param("expectedActiveGeneration") long expectedActiveGeneration,
            @Param("expectedLockVersion") long expectedLockVersion,
            @Param("now") Instant now);

    int insertGeneration(@Param("row") ApplicationAutomationReferenceGenerationRow row);

    int beginVerification(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("expectedLockVersion") long expectedLockVersion,
            @Param("traceId") String traceId,
            @Param("now") Instant now);

    int resetReplicaForVerification(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    void upsertTarget(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);

    int deferOfflineReplicas(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("liveServerIds") List<String> liveServerIds,
            @Param("now") Instant now);

    List<ApplicationAutomationReferenceReplicaRow> findClaimableReplicas(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now,
            @Param("limit") int limit);

    int claimReplica(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int renewLease(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int markReady(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("branch") String branch,
            @Param("commitHash") String commitHash,
            @Param("syncedAt") Instant syncedAt,
            @Param("now") Instant now);

    int markRetry(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    int markBlocked(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    int markVerificationResult(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("status") String status,
            @Param("actualBranch") String actualBranch,
            @Param("actualCommitHash") String actualCommitHash,
            @Param("verifiedAt") Instant verifiedAt,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    int updateGenerationResult(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("status") String status,
            @Param("lastError") String lastError,
            @Param("activatedAt") Instant activatedAt,
            @Param("now") Instant now);

    int completeState(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("status") String status,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    int terminateState(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("expectedLockVersion") long expectedLockVersion,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    int terminateGeneration(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    int terminateReplicas(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    int deleteRunLeases(@Param("runId") String runId);

    int insertRunLease(
            @Param("runId") String runId,
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now);
}
