package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 个人工作区搬迁 MyBatis mapper；关系型 SQL 只维护在 XML。 */
@Mapper
public interface PersonalWorkspaceRelocationMapper {

    List<PersonalWorkspaceRelocationCandidateRow> findMismatches(
            @Param("sourceLinuxServerId") String sourceLinuxServerId,
            @Param("limit") int limit);

    int discover(
            @Param("personalWorkspaceId") String personalWorkspaceId,
            @Param("runtimeWorkspaceId") String runtimeWorkspaceId,
            @Param("sourceLinuxServerId") String sourceLinuxServerId,
            @Param("targetLinuxServerId") String targetLinuxServerId,
            @Param("relocationId") String relocationId,
            @Param("traceId") String traceId,
            @Param("now") Instant now);

    List<PersonalWorkspaceRelocationRow> findClaimable(
            @Param("sourceLinuxServerId") String sourceLinuxServerId,
            @Param("now") Instant now,
            @Param("limit") int limit);

    int claim(
            @Param("relocationId") String relocationId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now);

    PersonalWorkspaceRelocationRow findByRelocationId(@Param("relocationId") String relocationId);

    int markTransferring(
            @Param("relocationId") String relocationId,
            @Param("leaseOwner") String leaseOwner,
            @Param("snapshotSha256") String snapshotSha256,
            @Param("archiveSizeBytes") long archiveSizeBytes,
            @Param("now") Instant now);

    int markApplying(
            @Param("relocationId") String relocationId,
            @Param("snapshotSha256") String snapshotSha256,
            @Param("archiveSizeBytes") long archiveSizeBytes,
            @Param("now") Instant now);

    String lockTargetCompletion(
            @Param("relocationId") String relocationId,
            @Param("sourceLinuxServerId") String sourceLinuxServerId,
            @Param("targetLinuxServerId") String targetLinuxServerId);

    int updateRuntimeWorkspaceTarget(
            @Param("relocationId") String relocationId,
            @Param("sourceLinuxServerId") String sourceLinuxServerId,
            @Param("targetLinuxServerId") String targetLinuxServerId,
            @Param("targetWorkspaceRootPath") String targetWorkspaceRootPath,
            @Param("traceId") String traceId,
            @Param("now") Instant now);

    int updatePersonalWorkspaceTarget(
            @Param("relocationId") String relocationId,
            @Param("targetRepoRootPath") String targetRepoRootPath,
            @Param("targetWorkspaceRootPath") String targetWorkspaceRootPath,
            @Param("baseCommit") String baseCommit,
            @Param("now") Instant now);

    int markTargetComplete(@Param("relocationId") String relocationId, @Param("now") Instant now);

    int reschedule(
            @Param("relocationId") String relocationId,
            @Param("leaseOwner") String leaseOwner,
            @Param("attemptCount") int attemptCount,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("safeErrorCode") String safeErrorCode,
            @Param("safeErrorMessage") String safeErrorMessage,
            @Param("now") Instant now);

    int completeCleanup(
            @Param("relocationId") String relocationId,
            @Param("leaseOwner") String leaseOwner,
            @Param("now") Instant now);
}
