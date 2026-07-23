package com.enterprise.testagent.domain.configuration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 企业内部模型供应商配置仓储端口；SQL 实现必须走 MyBatis XML。
 */
public interface InternalModelProviderRepository {

    List<InternalModelProvider> findAll();

    List<InternalModelProvider> findEnabled();

    List<InternalModelProviderRuntimeConfig> findEnabledRuntimeConfigs();

    Optional<InternalModelProvider> findByProviderId(String providerId);

    void replaceProviders(List<InternalModelProvider> providers, Instant updatedAt);

    Optional<String> findAuthToken();

    void saveAuthToken(String authToken, Instant updatedAt);
}
