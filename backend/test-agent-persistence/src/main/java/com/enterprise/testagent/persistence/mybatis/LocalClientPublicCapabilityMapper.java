package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 公共客户端能力包 SQL 映射；所有关系 SQL 只维护在 XML。 */
@Mapper
public interface LocalClientPublicCapabilityMapper {

    int insertRelease(LocalClientPublicCapabilityReleaseRow row);

    LocalClientPublicCapabilityReleaseRow findReleaseByDigest(@Param("bundleDigest") String bundleDigest);

    LocalClientPublicCapabilityReleaseRow findReleaseBySourceCommit(@Param("sourceCommit") String sourceCommit);

    LocalClientPublicCapabilityReleaseRow findLatestRelease();

    LocalClientPublicCapabilityReleaseRow findLatestAvailableRelease();

    LocalClientPublicCapabilityStateRow findInstanceState(@Param("clientInstanceId") String clientInstanceId);

    List<LocalClientPublicCapabilityStateRow> findUpdateAvailableStates(@Param("limit") int limit);

    int upsertInstanceState(LocalClientPublicCapabilityStateRow row);

    int projectInstanceState(LocalClientPublicCapabilityStateRow row);

    int markUpdateAvailableForCapableInstances(
            @Param("sourceCommit") String sourceCommit,
            @Param("bundleDigest") String bundleDigest,
            @Param("observedAt") Instant observedAt,
            @Param("capabilityName") String capabilityName);

    int insertAttempt(LocalClientPublicCapabilityAttemptRow row);

    LocalClientPublicCapabilityAttemptRow findAttempt(@Param("commandId") String commandId);

    LocalClientPublicCapabilityAttemptRow findAttemptByTarget(
            @Param("clientInstanceId") String clientInstanceId,
            @Param("targetDigest") String targetDigest);

    List<LocalClientPublicCapabilityAttemptRow> findDispatchableAttempts(@Param("limit") int limit);

    int bindAttemptGeneration(
            @Param("commandId") String commandId,
            @Param("expectedGeneration") long expectedGeneration,
            @Param("targetGeneration") long targetGeneration,
            @Param("observedAt") Instant observedAt);

    int transitionAttempt(
            @Param("commandId") String commandId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("terminal") boolean terminal,
            @Param("errorCode") String errorCode,
            @Param("observedAt") Instant observedAt);
}
