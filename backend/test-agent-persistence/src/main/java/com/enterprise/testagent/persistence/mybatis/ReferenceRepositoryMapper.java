package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 引用资产库 MyBatis mapper；所有关系型 SQL 只维护在 XML。 */
@Mapper
public interface ReferenceRepositoryMapper {

    ReferenceRepositoryStateRow findState(@Param("repositoryId") String repositoryId);

    List<ReferenceRepositoryStateRow> findStatesAfter(
            @Param("afterRepositoryId") String afterRepositoryId,
            @Param("limit") int limit);

    List<ReferenceRepositoryReplicaRow> findReplicas(@Param("repositoryId") String repositoryId);

    int initializeStateIfAbsent(@Param("row") ReferenceRepositoryStateRow row);

    int advanceStateIfCurrent(
            @Param("expectedGeneration") long expectedGeneration,
            @Param("row") ReferenceRepositoryStateRow row);

    void upsertTarget(
            @Param("repositoryId") String repositoryId,
            @Param("linuxServerId") String linuxServerId,
            @Param("generation") long generation,
            @Param("branch") String branch,
            @Param("now") Instant now);

    int deferOfflineReplicas(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("liveServerIds") List<String> liveServerIds,
            @Param("now") Instant now);

    int claimReplica(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    int renewLease(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    List<ReferenceRepositoryReplicaRow> findClaimableReplicas(
            @Param("linuxServerId") String linuxServerId,
            @Param("now") Instant now,
            @Param("limit") int limit);

    int markReady(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("branch") String branch,
            @Param("commitHash") String commitHash,
            @Param("syncedAt") Instant syncedAt,
            @Param("now") Instant now);

    int markRetry(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    int markBlocked(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("linuxServerId") String linuxServerId,
            @Param("leaseToken") String leaseToken,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    int updateOverallStatus(
            @Param("repositoryId") String repositoryId,
            @Param("generation") long generation,
            @Param("status") String status,
            @Param("lastError") String lastError,
            @Param("now") Instant now);
}
