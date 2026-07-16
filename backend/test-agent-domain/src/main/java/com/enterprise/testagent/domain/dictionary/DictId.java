package com.enterprise.testagent.domain.dictionary;

import java.time.Instant;
import java.util.Objects;

/**
 * 字典业务 ID 值对象。
 */
public record DictId(String value) {

    /**
     * 校验字典 ID 不为空且符合前缀约定。
     */
    public DictId {
        Objects.requireNonNull(value, "dictId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("dictId must not be blank");
        }
        if (!value.startsWith("dict_")) {
            throw new IllegalArgumentException("dictId must start with 'dict_'");
        }
    }
}
