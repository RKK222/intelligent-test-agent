package com.icbc.testagent.domain.opencodeprocess;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Linux 服务器稳定业务 ID，用于跨 Java、manager 和数据库关联同一台物理/虚拟服务器。
 */
public record LinuxServerId(String value) {

    private static final Pattern STABLE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    /**
     * 校验服务器 ID 只能包含 URL path、Redis key 和数据库索引中稳定可用的安全字符。
     */
    public LinuxServerId {
        Objects.requireNonNull(value, "linuxServerId must not be null");
        value = value.trim();
        if (!STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("linuxServerId must be 1-128 chars of letters, digits, dot, underscore or hyphen");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
