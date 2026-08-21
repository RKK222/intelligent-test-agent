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

    List<LocalClientWorkspaceRow> findWorkspacesByClientInstanceId(
            @Param("clientInstanceId") String clientInstanceId);

    int upsertWorkspace(LocalClientWorkspaceRow row);

    int deleteWorkspaceById(@Param("workspaceId") String workspaceId);
}
