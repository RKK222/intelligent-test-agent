package com.enterprise.testagent.domain.dictionary;

import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * 用户角色关联关系实体，关联用户和角色字典。
 */
public record UserRole(
        UserId userId,
        DictId dictId,
        Instant createdAt) {

    /**
     * 校验用户角色关联的不变量。
     */
    public UserRole {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(dictId, "dictId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * 创建用户角色关联的静态工厂方法。
     */
    public static UserRole create(UserId userId, DictId dictId) {
        return new UserRole(userId, dictId, Instant.now());
    }
}
