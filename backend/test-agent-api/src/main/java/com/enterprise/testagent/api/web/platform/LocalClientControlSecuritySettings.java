package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** client key、模型 grant 和控制隧道的传输安全边界；明文只允许显式本地开发开关。 */
@Component
public class LocalClientControlSecuritySettings {

    private final boolean allowInsecureControl;
    private final Set<String> trustedProxyAddresses;

    public LocalClientControlSecuritySettings(
            @Value("${test-agent.local-client.allow-insecure-control:false}") boolean allowInsecureControl,
            @Value("${test-agent.local-client.trusted-proxy-addresses:127.0.0.1,::1}")
                    String trustedProxyAddresses) {
        this.allowInsecureControl = allowInsecureControl;
        this.trustedProxyAddresses = parseTrustedProxyAddresses(trustedProxyAddresses);
    }

    public void requireSecure(URI uri, HttpHeaders headers, InetSocketAddress remoteAddress) {
        if (allowInsecureControl) {
            return;
        }
        String scheme = uri == null || uri.getScheme() == null
                ? ""
                : uri.getScheme().toLowerCase(Locale.ROOT);
        String forwardedProto = headers == null ? null : headers.getFirst("X-Forwarded-Proto");
        if ("https".equals(scheme)
                || "wss".equals(scheme)
                || (isSecureForwardedProto(forwardedProto) && isTrustedProxy(remoteAddress))) {
            return;
        }
        throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端认证和模型请求必须使用 HTTPS/WSS");
    }

    private boolean isTrustedProxy(InetSocketAddress remoteAddress) {
        return remoteAddress != null
                && remoteAddress.getAddress() != null
                && trustedProxyAddresses.contains(normalize(remoteAddress.getAddress()));
    }

    private static boolean isSecureForwardedProto(String forwardedProto) {
        return "https".equalsIgnoreCase(forwardedProto) || "wss".equalsIgnoreCase(forwardedProto);
    }

    private static Set<String> parseTrustedProxyAddresses(String configured) {
        if (configured == null || configured.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(LocalClientControlSecuritySettings::parseIpLiteral)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 只接受 IP 字面量，避免安全配置在启动时触发 DNS 解析并被域名漂移影响。 */
    private static String parseIpLiteral(String value) {
        if (!value.matches("[0-9A-Fa-f:.]+")) {
            throw new IllegalArgumentException("trusted proxy address must be an IP literal: " + value);
        }
        try {
            return normalize(InetAddress.getByName(value));
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("invalid trusted proxy address: " + value, exception);
        }
    }

    private static String normalize(InetAddress address) {
        return address.getHostAddress().toLowerCase(Locale.ROOT);
    }
}
