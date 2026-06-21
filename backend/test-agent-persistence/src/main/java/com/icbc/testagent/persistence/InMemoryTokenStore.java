package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.auth.TokenStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Token 存储，用于开发/测试环境或 Redis 不可用时作为降级方案。
 * 不支持分布式共享，重启后所有 Token 丢失。
 */
public final class InMemoryTokenStore implements TokenStore {

    private final Map<String, AuthPrincipal> store = new ConcurrentHashMap<>();

    @Override
    public void save(AuthPrincipal principal) {
        store.put(principal.token(), principal);
    }

    @Override
    public Optional<AuthPrincipal> findByToken(String token) {
        return Optional.ofNullable(store.get(token));
    }

    @Override
    public void delete(String token) {
        store.remove(token);
    }

    @Override
    public boolean isValid(String token) {
        AuthPrincipal principal = store.get(token);
        return principal != null && !principal.isExpired();
    }
}
