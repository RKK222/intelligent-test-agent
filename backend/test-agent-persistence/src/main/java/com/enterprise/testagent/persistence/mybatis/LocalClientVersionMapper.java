package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 客户端版本管理 SQL 映射；SQL 只维护在 LocalClientVersionMapper.xml。 */
@Mapper
public interface LocalClientVersionMapper {

    LocalClientReleaseRow findRelease(@Param("version") String version);

    List<LocalClientReleaseRow> findReleases();

    int insertRelease(LocalClientReleaseRow row);

    List<LocalClientReleaseArtifactRow> findReleaseArtifacts(@Param("version") String version);

    int insertReleaseArtifact(LocalClientReleaseArtifactRow row);

    Long nextPolicyRevision();

    LocalClientGlobalPolicyRow findGlobalPolicy();

    int upsertGlobalPolicy(LocalClientGlobalPolicyRow row);

    LocalClientUserPolicyRow findUserPolicy(@Param("userId") String userId);

    List<LocalClientUserPolicyRow> findUserPolicies();

    int upsertUserPolicy(LocalClientUserPolicyRow row);

    int insertPolicyAudit(
            @Param("auditId") String auditId,
            @Param("scope") String scope,
            @Param("userId") String userId,
            @Param("previousTargetVersion") String previousTargetVersion,
            @Param("targetVersion") String targetVersion,
            @Param("revision") long revision,
            @Param("action") String action,
            @Param("actorUserId") String actorUserId,
            @Param("createdAt") Instant createdAt);

    int insertRollout(LocalClientRolloutRow row);

    List<LocalClientRolloutRow> findRollouts();

    LocalClientRolloutRow findRollout(@Param("rolloutId") String rolloutId);

    LocalClientRolloutRow findRolloutForUpdate(@Param("rolloutId") String rolloutId);

    int completeRollout(
            @Param("rolloutId") String rolloutId,
            @Param("status") String status,
            @Param("completedAt") Instant completedAt);

    int insertAttempt(LocalClientUpdateAttemptRow row);

    List<LocalClientUpdateAttemptRow> findAttemptsByRollout(@Param("rolloutId") String rolloutId);

    LocalClientUpdateAttemptRow findAttempt(@Param("commandId") String commandId);

    List<LocalClientUpdateAttemptRow> findDispatchableAttempts(
            @Param("limit") int limit,
            @Param("offset") int offset,
            @Param("now") Instant now,
            @Param("deadlineCutoff") Instant deadlineCutoff);

    int transitionAttempt(
            @Param("commandId") String commandId,
            @Param("expectedStatus") String expectedStatus,
            @Param("status") String status,
            @Param("terminal") boolean terminal,
            @Param("releaseDigest") String releaseDigest,
            @Param("errorCode") String errorCode,
            @Param("observedAt") Instant observedAt);
}
