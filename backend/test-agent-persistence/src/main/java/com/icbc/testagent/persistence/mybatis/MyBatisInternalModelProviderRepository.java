package com.icbc.testagent.persistence.mybatis;

import com.icbc.testagent.domain.configuration.InternalModelProvider;
import com.icbc.testagent.domain.configuration.InternalModelProviderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 内部模型供应商配置的 MyBatis Repository 实现。
 */
@Repository
public class MyBatisInternalModelProviderRepository implements InternalModelProviderRepository {

    private final InternalModelProviderMapper mapper;

    public MyBatisInternalModelProviderRepository(InternalModelProviderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<InternalModelProvider> findAll() {
        return mapper.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<InternalModelProvider> findEnabled() {
        return mapper.findEnabled().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<InternalModelProvider> findByProviderId(String providerId) {
        return Optional.ofNullable(mapper.findByProviderId(providerId)).map(this::toDomain);
    }

    @Override
    @Transactional
    public void replaceProviders(List<InternalModelProvider> providers, Instant updatedAt) {
        if (providers == null || providers.isEmpty()) {
            mapper.deleteAllProviders();
            return;
        }
        providers.forEach(provider -> saveProvider(toRow(provider, updatedAt)));
        mapper.deleteProvidersNotIn(providers.stream().map(InternalModelProvider::providerId).toList());
    }

    @Override
    public Optional<String> findAuthToken() {
        return Optional.ofNullable(mapper.findSettings())
                .map(InternalModelProxySettingsRow::icbcOpenaiAuthToken)
                .filter(value -> !value.isBlank());
    }

    @Override
    public void saveAuthToken(String authToken, Instant updatedAt) {
        if (mapper.updateSettings(authToken, updatedAt) == 0) {
            mapper.insertSettings(authToken, updatedAt, updatedAt);
        }
    }

    private void saveProvider(InternalModelProviderRow row) {
        if (mapper.updateProvider(row) == 0) {
            mapper.insertProvider(row);
        }
    }

    private InternalModelProvider toDomain(InternalModelProviderRow row) {
        return new InternalModelProvider(
                row.providerId(),
                row.name(),
                row.baseUrl(),
                row.enabled(),
                row.sortOrder(),
                row.createdAt(),
                row.updatedAt());
    }

    private InternalModelProviderRow toRow(InternalModelProvider provider, Instant updatedAt) {
        Instant createdAt = provider.createdAt() == null ? updatedAt : provider.createdAt();
        return new InternalModelProviderRow(
                provider.providerId(),
                provider.name(),
                provider.baseUrl(),
                provider.enabled(),
                provider.sortOrder(),
                createdAt,
                updatedAt);
    }
}
