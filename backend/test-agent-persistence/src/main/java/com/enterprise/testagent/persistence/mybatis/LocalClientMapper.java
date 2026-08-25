package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 本地客户端关系型数据 MyBatis mapper；所有 SQL 均维护在 XML。 */
@Mapper
public interface LocalClientMapper {

    String lockCredentialUser(@Param("userId") String userId);

    LocalClientCredentialRow findCredentialByUserId(@Param("userId") String userId);

    LocalClientCredentialRow findCredentialByUserIdForUpdate(@Param("userId") String userId);

    LocalClientCredentialRow findActiveCredentialByFingerprint(@Param("fingerprint") String fingerprint);

    int upsertCredential(LocalClientCredentialRow row);

    LocalClientInstanceRow findInstanceById(@Param("clientInstanceId") String clientInstanceId);

    List<LocalClientInstanceRow> findInstancesByUserId(@Param("userId") String userId);

    List<LocalClientInstanceRow> findAllInstances();

    int upsertInstance(LocalClientInstanceRow row);

    int markInstanceDisconnected(
            @Param("clientInstanceId") String clientInstanceId,
            @Param("disconnectedAt") Instant disconnectedAt);

    int updateInstanceLastUpdateStatus(
            @Param("clientInstanceId") String clientInstanceId,
            @Param("status") String status,
            @Param("targetVersion") String targetVersion,
            @Param("observedAt") Instant observedAt);

    LocalClientWorkspaceRow findWorkspaceById(@Param("workspaceId") String workspaceId);

    LocalClientWorkspaceRow findWorkspaceByOwnerClientAndRootDigest(
            @Param("userId") String userId,
            @Param("clientInstanceId") String clientInstanceId,
            @Param("rootDigest") String rootDigest);

    List<LocalClientWorkspaceRow> findWorkspacesByOwnerRootIdentity(
            @Param("userId") String userId,
            @Param("rootDigest") String rootDigest,
            @Param("fileSystemIdentity") String fileSystemIdentity);

    List<LocalClientWorkspaceRow> findWorkspacesByClientInstanceId(
            @Param("clientInstanceId") String clientInstanceId);

    String lockWorkspaceRegistration(
            @Param("userId") String userId,
            @Param("clientInstanceId") String clientInstanceId);

    int upsertWorkspace(LocalClientWorkspaceRow row);

    int rebindWorkspace(
            @Param("workspaceId") String workspaceId,
            @Param("userId") String userId,
            @Param("expectedClientInstanceId") String expectedClientInstanceId,
            @Param("replacementClientInstanceId") String replacementClientInstanceId,
            @Param("normalizedRootPath") String normalizedRootPath,
            @Param("rootDigest") String rootDigest,
            @Param("fileSystemIdentity") String fileSystemIdentity,
            @Param("updatedAt") Instant updatedAt);

    int markInstanceReplaced(
            @Param("userId") String userId,
            @Param("replacedClientInstanceId") String replacedClientInstanceId,
            @Param("replacementClientInstanceId") String replacementClientInstanceId,
            @Param("replacedAt") Instant replacedAt);

    int clearInstanceReplacement(@Param("clientInstanceId") String clientInstanceId);

    int deleteWorkspaceById(@Param("workspaceId") String workspaceId);

    boolean isRolloutEnabled(@Param("userId") String userId);

    LocalClientRolloutUserRow findRolloutByUserId(@Param("userId") String userId);

    List<LocalClientRolloutUserRow> findEnabledRolloutPage(
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countEnabledRollout();

    int upsertRollout(LocalClientRolloutUserRow row);

    int disableRollout(
            @Param("userId") String userId,
            @Param("updatedByUserId") String updatedByUserId,
            @Param("updatedAt") Instant updatedAt);
}
