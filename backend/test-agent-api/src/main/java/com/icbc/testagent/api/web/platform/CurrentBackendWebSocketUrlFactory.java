package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.net.URI;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 当前 Java 节点的 WebSocket 绝对地址生成器。
 *
 * <p>一次性 ticket 保存在签发节点内存，响应必须把浏览器固定到同一 Java，不能再交给入口负载均衡器随机选择。
 */
@Component
class CurrentBackendWebSocketUrlFactory {

    private final BackendInstanceIdentity backendIdentity;

    CurrentBackendWebSocketUrlFactory(BackendInstanceIdentity backendIdentity) {
        this.backendIdentity = Objects.requireNonNull(backendIdentity, "backendIdentity must not be null");
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
}
