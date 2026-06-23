package com.icbc.testagent.domain.configuration;

/**
 * 外部系统同步的应用 ID，不强制平台前缀，只要求非空。
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
