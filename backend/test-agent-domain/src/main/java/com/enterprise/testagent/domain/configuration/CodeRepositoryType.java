package com.enterprise.testagent.domain.configuration;

import java.util.Arrays;

/**
 * 版本库类型编码，编码值与通用字典 {@code REPOSITORY_TYPE} 的 {@code dict_value} 保持一致。
 */
public enum CodeRepositoryType {
    TEST_WORK_REPOSITORY("TEST_WORK_REPOSITORY", true),
    APPLICATION_CODE_REPOSITORY("APPLICATION_CODE_REPOSITORY", false),
    APPLICATION_ASSET_REPOSITORY("APPLICATION_ASSET_REPOSITORY", false);

    private final String value;
    private final boolean standard;

    CodeRepositoryType(String value, boolean standard) {
        this.value = value;
        this.standard = standard;
    }

    public String value() {
        return value;
    }

    /**
     * 是否映射到旧版“标准库”兼容字段。
     */
    public boolean standard() {
        return standard;
    }

    /**
     * 旧客户端只传 standard 时，按原语义推导版本库类型。
     */
    public static CodeRepositoryType fromStandard(boolean standard) {
        return standard ? TEST_WORK_REPOSITORY : APPLICATION_CODE_REPOSITORY;
    }

    /**
     * 按字典编码解析类型，未知编码直接拒绝，避免把脏值写入领域对象。
     */
    public static CodeRepositoryType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown code repository type: " + value));
    }
}
