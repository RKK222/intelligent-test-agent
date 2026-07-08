package com.icbc.testagent.persistence.mybatis;

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
}
