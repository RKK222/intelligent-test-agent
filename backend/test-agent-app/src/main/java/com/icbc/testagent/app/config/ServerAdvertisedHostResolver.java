package com.icbc.testagent.app.config;

import com.icbc.testagent.common.net.LinuxServerIpResolver;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 解析当前服务器对 Java 集群、manager 和 opencode 端口可访问的主机地址。
 */
final class ServerAdvertisedHostResolver {

    static final String ADVERTISED_HOST_ENV = "TEST_AGENT_SERVER_ADVERTISED_HOST";
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,254}$");

    private final Map<String, String> env;
    private final LinuxServerIpResolver linuxServerIpResolver;

    ServerAdvertisedHostResolver(LinuxServerIpResolver linuxServerIpResolver) {
        this(System.getenv(), linuxServerIpResolver);
    }

    ServerAdvertisedHostResolver(Map<String, String> env, LinuxServerIpResolver linuxServerIpResolver) {
        this.env = Objects.requireNonNull(env, "env must not be null");
        this.linuxServerIpResolver = Objects.requireNonNull(linuxServerIpResolver, "linuxServerIpResolver must not be null");
    }

    /**
     * 环境变量可覆盖自动探测地址；缺失时使用现有内网 IPv4 探测结果。
     * 测试环境下如果环境变量未设置，默认使用 127.0.0.1。
     */
    String resolve() {
        String configured = env.get(ADVERTISED_HOST_ENV);
        String host;
        if (configured != null && !configured.isBlank()) {
            host = configured.trim();
        } else {
            String activeProfile = env.get("SPRING_PROFILES_ACTIVE");
            if ("test".equals(activeProfile)) {
                host = "127.0.0.1";
            } else {
                host = linuxServerIpResolver.resolve();
            }
        }
        return normalizeHost(host);
    }

    /**
     * 校验并规整 host-only 地址，禁止混入 scheme、端口或路径。
     */
    static String normalizeHost(String host) {
        host = host == null ? "" : host.trim();
        if (!HOST.matcher(host).matches()) {
            throw new IllegalArgumentException(ADVERTISED_HOST_ENV + " must be a host name or IPv4 literal without scheme or port");
        }
        return host;
    }
}
