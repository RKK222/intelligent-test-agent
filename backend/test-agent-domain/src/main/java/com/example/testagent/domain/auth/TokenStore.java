package com.example.testagent.domain.auth;

import java.util.Optional;

/**
 * Token 存储接口，定义 Token 的持久化和校验操作。
 * 目前使用 Redis 实现，后续可扩展为其他存储后端。
 */
public interface TokenStore {

    /**
     * 保存认证主体到 Token 存储，设置过期时间。
     */
    void save(AuthPrincipal principal);

    /**
     * 根据 Token 查找认证主体。
     */
    Optional<AuthPrincipal> findByToken(String token);

    /**
     * 删除 Token（登出时调用）。
     */
    void delete(String token);

    /**
     * 检查 Token 是否存在且有效。
     */
    boolean isValid(String token);
}
