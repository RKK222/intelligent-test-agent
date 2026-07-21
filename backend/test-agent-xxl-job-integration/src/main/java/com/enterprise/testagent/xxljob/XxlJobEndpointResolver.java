package com.enterprise.testagent.xxljob;

import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/**
 * 从平台已经解析的 advertised host 派生 XXL 本机 Admin 与对集群公布的 executor 地址。
 * 本类不读取 XXL 专用地址环境变量，避免扩容时要求其它 Java 更新静态节点列表。
 */
final class XxlJobEndpointResolver {

    /**
     * 解析当前 Java 的两个 XXL endpoint；Admin 始终走同 JVM loopback，executor 使用平台可达 host。
     */
    Endpoints resolve(XxlJobProperties properties, BackendInstanceIdentity backendIdentity) {
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(backendIdentity, "backendIdentity must not be null");

        String adminAddress = buildAddress(
                "127.0.0.1",
                properties.getAdmin().getPort(),
                normalizeContextPath(properties.getAdmin().getContextPath()));
        String advertisedHost = advertisedHost(backendIdentity.listenUrl());
        String executorAddress = buildAddress(advertisedHost, properties.getExecutor().getPort(), null);
        return new Endpoints(adminAddress, executorAddress);
    }

    private static String advertisedHost(String listenUrl) {
        try {
            URI uri = URI.create(listenUrl == null ? "" : listenUrl.trim());
            String scheme = uri.getScheme();
            String normalizedScheme = scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
            if (!("http".equals(normalizedScheme) || "https".equals(normalizedScheme))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw invalidListenUrl();
            }
            return uri.getHost();
        } catch (IllegalArgumentException exception) {
            throw invalidListenUrl();
        }
    }

    private static String buildAddress(String host, int port, String path) {
        try {
            return new URI("http", null, host, port, path, null, null).toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("无法生成 XXL-JOB 节点地址", exception);
        }
    }

    private static String normalizeContextPath(String contextPath) {
        String normalized = contextPath == null ? "" : contextPath.trim();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static IllegalStateException invalidListenUrl() {
        // 不把原始 listenUrl 放入异常，避免其中意外混入凭据后进入启动日志。
        return new IllegalStateException("无法从平台后端监听地址派生 XXL executor 地址");
    }

    /** 已完成校验、可直接传给 XXL 上游组件的节点地址。 */
    record Endpoints(String adminAddress, String executorAddress) {

        Endpoints {
            if (adminAddress == null || adminAddress.isBlank()) {
                throw new IllegalArgumentException("adminAddress must not be blank");
            }
            if (executorAddress == null || executorAddress.isBlank()) {
                throw new IllegalArgumentException("executorAddress must not be blank");
            }
        }
    }
}
