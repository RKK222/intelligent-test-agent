package com.icbc.testagent.domain.opencodeprocess;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Linux 服务器业务 ID，当前按部署约定直接使用服务器 IPv4 地址。
 */
public record LinuxServerId(String value) {

    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
                    + "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    /**
     * 校验服务器 ID 必须是可用于 http://{linuxServerIp}:{port} 的 IPv4 地址。
     */
    public LinuxServerId {
        Objects.requireNonNull(value, "linuxServerId must not be null");
        if (!IPV4.matcher(value).matches()) {
            throw new IllegalArgumentException("linuxServerId must be an IPv4 address");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
