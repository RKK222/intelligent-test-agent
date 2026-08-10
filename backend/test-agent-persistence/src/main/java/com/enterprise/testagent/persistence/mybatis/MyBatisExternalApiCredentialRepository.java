package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredential;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialId;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialRepository;
import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 外部 API 凭据 MyBatis 仓储实现；聚合和 scope 通过 XML SQL 共同持久化。 */
@Repository
public class MyBatisExternalApiCredentialRepository implements ExternalApiCredentialRepository {

    private final ExternalApiCredentialMapper mapper;

    public MyBatisExternalApiCredentialRepository(ExternalApiCredentialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ExternalApiCredential> findAll() {
        return assemble(mapper.findAllCredentials());
    }

    @Override
    public PageResponse<ExternalApiCredential> findPage(
            String keyword, Boolean enabled, PageRequest pageRequest) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<ExternalApiCredentialRow> rows = mapper.findCredentialPage(
                normalizedKeyword, enabled, pageRequest.size(), pageRequest.offset());
        return new PageResponse<>(
                assemble(rows), pageRequest.page(), pageRequest.size(),
                mapper.countCredentials(normalizedKeyword, enabled));
    }

    @Override
    public Optional<ExternalApiCredential> findById(ExternalApiCredentialId credentialId) {
        ExternalApiCredentialRow row = mapper.findCredentialById(credentialId.value());
        if (row == null) {
            return Optional.empty();
        }
        return assemble(List.of(row)).stream().findFirst();
    }

    @Override
    public boolean existsByToolCode(String toolCode) {
        return mapper.countByToolCode(toolCode) > 0;
    }

    @Override
    @Transactional
    public void insert(ExternalApiCredential credential) {
        mapper.insertCredential(toRow(credential));
        insertScopes(credential);
    }

    @Override
    @Transactional
    public void updateDetails(ExternalApiCredential credential) {
        mapper.updateDetails(
                credential.credentialId().value(), credential.toolName(), credential.enabled(), credential.updatedAt());
        mapper.deleteScopes(credential.credentialId().value());
        insertScopes(credential);
    }

    @Override
    public void updateKey(ExternalApiCredential credential) {
        mapper.updateKey(
                credential.credentialId().value(), credential.encryptedApiKey(),
                credential.apiKeyFingerprint(), credential.keyHint(), credential.updatedAt());
    }

    @Override
    @Transactional
    public boolean delete(ExternalApiCredentialId credentialId) {
        return mapper.deleteCredential(credentialId.value()) > 0;
    }

    private List<ExternalApiCredential> assemble(List<ExternalApiCredentialRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> ids = rows.stream().map(ExternalApiCredentialRow::credentialId).toList();
        Map<String, Set<ExternalApiScope>> scopes = new HashMap<>();
        for (ExternalApiCredentialScopeRow scopeRow : mapper.findScopesByCredentialIds(ids)) {
            scopes.computeIfAbsent(scopeRow.credentialId(), ignored -> new LinkedHashSet<>())
                    .add(ExternalApiScope.fromValue(scopeRow.scopeCode()));
        }
        return rows.stream().map(row -> new ExternalApiCredential(
                new ExternalApiCredentialId(row.credentialId()), row.toolCode(), row.toolName(),
                row.encryptedApiKey(), row.apiKeyFingerprint(), row.keyHint(), row.enabled(),
                scopes.getOrDefault(row.credentialId(), Set.of()), row.createdAt(), row.updatedAt())).toList();
    }

    private void insertScopes(ExternalApiCredential credential) {
        credential.scopes().stream().sorted().forEach(scope -> mapper.insertScope(
                new ExternalApiCredentialScopeRow(credential.credentialId().value(), scope.name())));
    }

    private static ExternalApiCredentialRow toRow(ExternalApiCredential credential) {
        return new ExternalApiCredentialRow(
                credential.credentialId().value(), credential.toolCode(), credential.toolName(),
                credential.encryptedApiKey(), credential.apiKeyFingerprint(), credential.keyHint(),
                credential.enabled(), credential.createdAt(), credential.updatedAt());
    }
}
