package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 外部 API 凭据 MyBatis mapper；所有关系型 SQL 维护在 XML。 */
@Mapper
public interface ExternalApiCredentialMapper {

    List<ExternalApiCredentialRow> findAllCredentials();

    List<ExternalApiCredentialRow> findCredentialPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("limit") int limit,
            @Param("offset") long offset);

    long countCredentials(@Param("keyword") String keyword, @Param("enabled") Boolean enabled);

    ExternalApiCredentialRow findCredentialById(@Param("credentialId") String credentialId);

    List<ExternalApiCredentialScopeRow> findScopesByCredentialIds(
            @Param("credentialIds") List<String> credentialIds);

    long countByToolCode(@Param("toolCode") String toolCode);

    int insertCredential(ExternalApiCredentialRow row);

    int insertScope(ExternalApiCredentialScopeRow row);

    int deleteScopes(@Param("credentialId") String credentialId);

    int updateDetails(
            @Param("credentialId") String credentialId,
            @Param("toolName") String toolName,
            @Param("enabled") boolean enabled,
            @Param("updatedAt") Instant updatedAt);

    int updateKey(
            @Param("credentialId") String credentialId,
            @Param("encryptedApiKey") String encryptedApiKey,
            @Param("apiKeyFingerprint") String apiKeyFingerprint,
            @Param("keyHint") String keyHint,
            @Param("updatedAt") Instant updatedAt);

    int deleteCredential(@Param("credentialId") String credentialId);
}
