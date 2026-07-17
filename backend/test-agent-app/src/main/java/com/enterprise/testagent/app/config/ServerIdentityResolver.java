package com.enterprise.testagent.app.config;

import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 解析当前服务器稳定身份，避免服务器迁移或换 IP 后改变 linuxServerId。
 */
final class ServerIdentityResolver {

    static final String SERVER_ID_ENV = "TEST_AGENT_LINUX_SERVER_ID";

    private final Map<String, String> env;
    private final Supplier<String> hostnameSupplier;

    ServerIdentityResolver() {
        this(System.getenv(), ServerIdentityResolver::systemHostname);
    }

    ServerIdentityResolver(Map<String, String> env, Supplier<String> hostnameSupplier) {
        this.env = Objects.requireNonNull(env, "env must not be null");
        this.hostnameSupplier = Objects.requireNonNull(hostnameSupplier, "hostnameSupplier must not be null");
    }

    /**
     * 环境变量优先；缺失时读取当前 Java 主机名并复用 LinuxServerId 的稳定字符集校验。
     */
    LinuxServerId resolve() {
        String configured = env.get(SERVER_ID_ENV);
        if (configured != null && !configured.isBlank()) {
            return new LinuxServerId(configured);
        }
        String hostname = hostnameSupplier.get();
        if (hostname == null || hostname.isBlank()) {
            throw new IllegalStateException("未配置 " + SERVER_ID_ENV + "，且无法读取机器名称");
        }
        return new LinuxServerId(hostname);
    }

    private static String systemHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("读取机器名称失败: " + exception.getMessage(), exception);
        }
    }
}
