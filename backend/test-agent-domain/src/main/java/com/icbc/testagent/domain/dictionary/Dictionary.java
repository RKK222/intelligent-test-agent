package com.icbc.testagent.domain.dictionary;

import java.time.Instant;
import java.util.Objects;

/**
 * 字典聚合根，存储系统通用字典数据。
 */
public record Dictionary(
        DictId dictId,
        String dictName,
        String dictKey,
        String dictValue,
        String dictLabel,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 字典 Key 常量定义。
     */
    public static final String DICT_KEY_ROLE = "ROLE";

    /**
     * 字典 Value 常量定义 - 角色类型。
     */
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String ROLE_APP_ADMIN = "APP_ADMIN";
    public static final String ROLE_USER = "USER";

    /**
     * 校验字典的不变量。
     */
    public Dictionary {
        Objects.requireNonNull(dictId, "dictId must not be null");
        Objects.requireNonNull(dictName, "dictName must not be null");
        Objects.requireNonNull(dictKey, "dictKey must not be null");
        Objects.requireNonNull(dictValue, "dictValue must not be null");
        Objects.requireNonNull(dictLabel, "dictLabel must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (dictName.isBlank()) {
            throw new IllegalArgumentException("dictName must not be blank");
        }
        if (dictKey.isBlank()) {
            throw new IllegalArgumentException("dictKey must not be blank");
        }
        if (dictValue.isBlank()) {
            throw new IllegalArgumentException("dictValue must not be blank");
        }
        if (dictLabel.isBlank()) {
            throw new IllegalArgumentException("dictLabel must not be blank");
        }
    }
}
