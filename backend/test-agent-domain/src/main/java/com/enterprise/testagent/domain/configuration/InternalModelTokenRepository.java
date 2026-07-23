package com.enterprise.testagent.domain.configuration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 内部模型 Token 定义仓储端口；Token 明文只能用于写入或运行时联表加载。
 */
public interface InternalModelTokenRepository {

    List<InternalModelToken> findAll();

    Optional<InternalModelToken> findById(long tokenId);

    Optional<InternalModelToken> findByName(String name);

    Optional<InternalModelToken> findLegacyDefault();

    InternalModelToken create(String name, String authToken, Instant now);

    InternalModelToken upsertLegacyDefault(String authToken, Instant now);

    Optional<InternalModelToken> update(long tokenId, String name, String authToken, Instant now);

    boolean deleteIfUnreferenced(long tokenId);
}
