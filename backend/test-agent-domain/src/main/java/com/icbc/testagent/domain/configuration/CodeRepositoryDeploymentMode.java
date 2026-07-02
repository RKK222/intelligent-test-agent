package com.icbc.testagent.domain.configuration;

import java.util.Arrays;

/**
 * 版本库部署模式：外部模式保存完整 Git URL，内部模式保存不含统一认证号的 SCM 地址片段。
 */
public enum CodeRepositoryDeploymentMode {
    EXTERNAL("EXTERNAL"),
    INTERNAL("INTERNAL");

    private final String value;

    CodeRepositoryDeploymentMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * 数据库或接口未传值时按存量兼容语义走外部模式。
     */
    public static CodeRepositoryDeploymentMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return EXTERNAL;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return Arrays.stream(values())
                .filter(mode -> mode.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown code repository deployment mode: " + value));
    }

    /**
     * 系统部署配置使用 external/internal 小写值，这里统一转为版本库字段枚举值。
     */
    public static CodeRepositoryDeploymentMode fromDeploymentProperty(String value) {
        return "internal".equalsIgnoreCase(value == null ? "" : value.trim()) ? INTERNAL : EXTERNAL;
    }
}
