package com.icbc.testagent.domain.configuration;

/**
 * 应用 ID 不强制平台前缀，只要求非空；创建入口会额外限制数据库字段长度。
 */
public record ApplicationId(String value) {

    /**
     * 校验外部应用 ID 不能为空。
     */
    public ApplicationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("appId must not be blank");
        }
        value = value.trim();
    }

    @Override
    public String toString() {
        return value;
    }
}
