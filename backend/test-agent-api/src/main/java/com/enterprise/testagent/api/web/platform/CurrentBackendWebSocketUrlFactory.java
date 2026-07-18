package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.net.URI;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * 当前 Java 节点的 WebSocket 绝对地址生成器。
 *
 * <p>一次性 ticket 保存在签发节点内存，响应必须把浏览器固定到同一 Java，不能再交给入口负载均衡器随机选择。
 */
@Component
class CurrentBackendWebSocketUrlFactory {

    private final BackendInstanceIdentity backendIdentity;
    private final String publicTerminalBaseUrl;
    private final boolean allowInsecureServerWebSocket;

    @Autowired
    CurrentBackendWebSocketUrlFactory(
            BackendInstanceIdentity backendIdentity,
            @Value("${test-agent.terminal.public-websocket-base-url:}") String publicTerminalBaseUrl,
            @Value("${test-agent.terminal.allow-insecure-server-websocket:false}") boolean allowInsecureServerWebSocket) {
        this.backendIdentity = Objects.requireNonNull(backendIdentity, "backendIdentity must not be null");
        this.publicTerminalBaseUrl = publicTerminalBaseUrl == null ? "" : publicTerminalBaseUrl.trim();
        this.allowInsecureServerWebSocket = allowInsecureServerWebSocket;
    }

    CurrentBackendWebSocketUrlFactory(BackendInstanceIdentity backendIdentity) {
        this(backendIdentity, "", false);
    }

    String absoluteUrl(String pathAndQuery) {
        if (pathAndQuery == null || !pathAndQuery.startsWith("/")) {
            throw new IllegalArgumentException("pathAndQuery must start with /");
        }
        URI listenUri;
        try {
            listenUri = URI.create(backendIdentity.listenUrl());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("当前后端监听地址无法生成 WebSocket URL", exception);
        }
        String authority = listenUri.getRawAuthority();
        if (authority == null || authority.isBlank() || listenUri.getRawQuery() != null || listenUri.getRawFragment() != null) {
            throw new IllegalStateException("当前后端监听地址无法生成 WebSocket URL");
        }
        String webSocketScheme = switch (listenUri.getScheme() == null ? "" : listenUri.getScheme().toLowerCase()) {
            case "http", "ws" -> "ws";
            case "https", "wss" -> "wss";
            default -> throw new IllegalStateException("当前后端监听地址协议无法生成 WebSocket URL");
        };
        String basePath = listenUri.getRawPath();
        String normalizedBasePath = basePath == null || basePath.isBlank() || "/".equals(basePath)
                ? ""
                : (basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath);
        return webSocketScheme + "://" + authority + normalizedBasePath + pathAndQuery;
    }

    /**
     * 正式环境的服务器终端只返回统一 HTTPS 网关的 WSS 地址；本地 test profile 可显式允许直连。
     */
    String serverTerminalUrl(String pathAndQuery) {
        if (pathAndQuery == null || !pathAndQuery.startsWith("/")) {
            throw new IllegalArgumentException("pathAndQuery must start with /");
        }
        if (publicTerminalBaseUrl.isBlank()) {
            if (allowInsecureServerWebSocket) {
                return absoluteUrl(pathAndQuery);
            }
            throw new PlatformException(ErrorCode.TERMINAL_UNAVAILABLE, "服务器终端 WSS 网关未配置");
        }
        URI base;
        try {
            base = URI.create(publicTerminalBaseUrl);
        } catch (RuntimeException exception) {
            throw new PlatformException(
                    ErrorCode.TERMINAL_UNAVAILABLE,
                    "服务器终端 WSS 网关配置无效",
                    java.util.Map.of(),
                    exception);
        }
        if (!"wss".equalsIgnoreCase(base.getScheme()) || base.getRawAuthority() == null
                || base.getRawQuery() != null || base.getRawFragment() != null) {
            throw new PlatformException(ErrorCode.TERMINAL_UNAVAILABLE, "服务器终端必须配置 wss 网关地址");
        }
        String path = base.getRawPath();
        String normalized = path == null || path.isBlank() || "/".equals(path)
                ? "" : (path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
        return "wss://" + base.getRawAuthority() + normalized + pathAndQuery;
    }
}
