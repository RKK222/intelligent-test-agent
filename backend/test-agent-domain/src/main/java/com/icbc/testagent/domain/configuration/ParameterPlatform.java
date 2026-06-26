package com.icbc.testagent.domain.configuration;

import java.util.Locale;

/**
 * 通用参数适用平台。数据库保存小写稳定值，业务读取时按当前 JVM 平台做精确匹配。
 */
public enum ParameterPlatform {
    WINDOWS("windows"),
    LINUX("linux"),
    ALL("all");

    private final String value;

    ParameterPlatform(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ParameterPlatform fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("platform must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ParameterPlatform platform : values()) {
            if (platform.value.equals(normalized)) {
                return platform;
            }
        }
        throw new IllegalArgumentException("unsupported parameter platform: " + value);
    }

    public static ParameterPlatform current() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.startsWith("windows") ? WINDOWS : LINUX;
    }
}
