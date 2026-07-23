package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.configuration.InternalModelToken;
import com.enterprise.testagent.domain.configuration.InternalModelTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 内部模型 Token 定义的 MyBatis Repository；Token 明文只进入写 SQL，不进入返回模型。
 */
@Repository
public class MyBatisInternalModelTokenRepository implements InternalModelTokenRepository {

    private static final String LEGACY_DEFAULT_NAME = "默认 Token";

    private final InternalModelProviderMapper mapper;

    public MyBatisInternalModelTokenRepository(InternalModelProviderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<InternalModelToken> findAll() {
        return mapper.findAllTokens().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<InternalModelToken> findById(long tokenId) {
        return Optional.ofNullable(mapper.findTokenById(tokenId)).map(this::toDomain);
    }

    @Override
    public Optional<InternalModelToken> findByName(String name) {
        return Optional.ofNullable(mapper.findTokenByName(name)).map(this::toDomain);
    }

    @Override
    public Optional<InternalModelToken> findLegacyDefault() {
        return Optional.ofNullable(mapper.findLegacyDefaultToken()).map(this::toDomain);
    }

    @Override
    @Transactional
    public InternalModelToken create(String name, String authToken, Instant now) {
        mapper.insertToken(name, authToken, null, now, now);
        return findByName(name).orElseThrow(() -> new IllegalStateException("新增内部模型 Token 后无法读取"));
    }

    @Override
    @Transactional
    public InternalModelToken upsertLegacyDefault(String authToken, Instant now) {
        if (mapper.updateLegacyDefaultToken(authToken, now) == 0) {
            mapper.insertToken(LEGACY_DEFAULT_NAME, authToken, "default", now, now);
        }
        return findLegacyDefault().orElseThrow(() -> new IllegalStateException("保存兼容默认 Token 后无法读取"));
    }

    @Override
    @Transactional
    public Optional<InternalModelToken> update(long tokenId, String name, String authToken, Instant now) {
        if (mapper.updateToken(tokenId, name, authToken, now) == 0) {
            return Optional.empty();
        }
        return findById(tokenId);
    }

    @Override
    @Transactional
    public boolean deleteIfUnreferenced(long tokenId) {
        return mapper.deleteTokenIfUnreferenced(tokenId) > 0;
    }

    private InternalModelToken toDomain(InternalModelTokenRow row) {
        return new InternalModelToken(
                row.tokenId(),
                row.name(),
                row.referencedProviderCount(),
                row.createdAt(),
                row.updatedAt());
    }
}
