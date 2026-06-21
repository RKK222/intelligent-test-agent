package com.icbc.testagent.domain.user;

import java.util.Objects;

/**
 * 用户业务 ID 值对象，封装用户 ID 的校验和不变性。
 */
public record UserId(String value) {

    /**
     * 校验用户 ID 不为空且符合前缀约定。
     */
    public UserId {
        Objects.requireNonNull(value, "userId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (!value.startsWith("usr_")) {
            throw new IllegalArgumentException("userId must start with 'usr_'");
        }
    }
}
