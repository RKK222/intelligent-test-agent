package com.enterprise.testagent.opencode.runtime.process.socket;

import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * manager 控制面运行参数，由 test-agent-app 从外部配置绑定后提供给 runtime/API。
 */
public record ManagerControlSettings(
        String token,
        String listenUrl,
        LinuxServerId linuxServerId,
        String advertisedHost,
        Duration heartbeatInterval,
        Duration backendStaleAfter,
        Duration commandTimeout,
        int backendDiscoveryLimit) {

    /**
     * 兼容旧测试构造器；advertisedHost 从 listenUrl 中提取，提取失败时回退为 linuxServerId 文本。
     */
    public ManagerControlSettings(
            String token,
            String listenUrl,
            LinuxServerId linuxServerId,
            Duration heartbeatInterval,
            Duration backendStaleAfter,
            Duration commandTimeout,
            int backendDiscoveryLimit) {
        this(
                token,
                listenUrl,
                linuxServerId,
                advertisedHostFromListenUrl(listenUrl, linuxServerId),
                heartbeatInterval,
                backendStaleAfter,
                commandTimeout,
                backendDiscoveryLimit);
    }

    /**
     * 规整时间和上限，避免配置错误造成零超时或无限列表。
     */
    public ManagerControlSettings {
        token = token == null ? "" : token.trim();
        listenUrl = requireText(listenUrl, "listenUrl");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        advertisedHost = requireText(advertisedHost, "advertisedHost");
        heartbeatInterval = positive(heartbeatInterval, Duration.ofSeconds(5));
        backendStaleAfter = positive(backendStaleAfter, Duration.ofSeconds(10));
        commandTimeout = positive(commandTimeout, Duration.ofSeconds(10));
        if (backendDiscoveryLimit < 1 || backendDiscoveryLimit > 500) {
            backendDiscoveryLimit = 100;
        }
    }

    /**
     * 校验 Authorization 头是否匹配 manager 控制面专用 token。
     */
    public boolean tokenMatches(String authorizationHeader) {
        if (token.isBlank() || authorizationHeader == null || authorizationHeader.isBlank()) {
            return false;
        }
        return ("Bearer " + token).equals(authorizationHeader.trim());
    }

    /**
     * 根据后端 HTTP 直连地址派生 manager WebSocket 地址。
     */
    public String webSocketUrl() {
        String trimmed = listenUrl.endsWith("/") ? listenUrl.substring(0, listenUrl.length() - 1) : listenUrl;
        if (trimmed.startsWith("https://")) {
            return "wss://" + trimmed.substring("https://".length()) + "/api/internal/platform/opencode-runtime/manager/ws";
        }
        if (trimmed.startsWith("http://")) {
            return "ws://" + trimmed.substring("http://".length()) + "/api/internal/platform/opencode-runtime/manager/ws";
        }
        return trimmed + "/api/internal/platform/opencode-runtime/manager/ws";
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String advertisedHostFromListenUrl(String listenUrl, LinuxServerId linuxServerId) {
        if (listenUrl != null && !listenUrl.isBlank()) {
            try {
                String host = URI.create(listenUrl.trim()).getHost();
                if (host != null && !host.isBlank()) {
                    return host;
                }
            } catch (IllegalArgumentException ignored) {
                // 旧构造器只用于兼容测试和少量内部调用；解析失败时用稳定 ID 兜底。
            }
        }
        return linuxServerId == null ? "" : linuxServerId.value();
    }
}
