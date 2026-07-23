package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 内部模型供应商 MyBatis mapper；SQL 统一维护在 XML 中。
 */
@Mapper
public interface InternalModelProviderMapper {

    List<InternalModelProviderRow> findAll();

    List<InternalModelProviderRow> findEnabled();

    List<InternalModelProviderRuntimeRow> findEnabledRuntimeConfigs();

    InternalModelProviderRow findByProviderId(@Param("providerId") String providerId);

    int updateProvider(InternalModelProviderRow row);

    void insertProvider(InternalModelProviderRow row);

    int deleteProvidersNotIn(@Param("providerIds") List<String> providerIds);

    int deleteAllProviders();

    InternalModelProxySettingsRow findSettings();

    int updateSettings(
            @Param("authToken") String authToken,
            @Param("updatedAt") Instant updatedAt);

    void insertSettings(
            @Param("authToken") String authToken,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt);

    List<InternalModelTokenRow> findAllTokens();

    InternalModelTokenRow findTokenById(@Param("tokenId") long tokenId);

    InternalModelTokenRow findTokenByName(@Param("name") String name);

    InternalModelTokenRow findLegacyDefaultToken();

    void insertToken(
            @Param("name") String name,
            @Param("authToken") String authToken,
            @Param("legacyKey") String legacyKey,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt);

    int updateToken(
            @Param("tokenId") long tokenId,
            @Param("name") String name,
            @Param("authToken") String authToken,
            @Param("updatedAt") Instant updatedAt);

    int updateLegacyDefaultToken(
            @Param("authToken") String authToken,
            @Param("updatedAt") Instant updatedAt);

    int deleteTokenIfUnreferenced(@Param("tokenId") long tokenId);
}
