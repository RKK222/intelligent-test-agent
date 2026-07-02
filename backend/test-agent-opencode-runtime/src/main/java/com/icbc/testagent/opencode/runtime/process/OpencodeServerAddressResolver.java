package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.domain.support.DomainValidation;
import java.net.URI;

/**
 * opencode server 网络地址解析器；服务器稳定 ID 只做归属，不参与 URL 拼接。
 */
final class OpencodeServerAddressResolver {

    private final String advertisedHost;

    OpencodeServerAddressResolver(String advertisedHost) {
        this.advertisedHost = DomainValidation.requireText(advertisedHost, "advertisedHost");
    }

    /**
     * 根据当前服务器可访问主机名/IP 和端口生成 opencode baseUrl。
     */
    String baseUrl(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        return "http://" + advertisedHost + ":" + port;
    }

    /**
     * 返回头像和运行管理展示用的 host:port，优先从已持久化 baseUrl 解析。
     */
    String serviceAddress(String baseUrl, int port) {
        String host = host(baseUrl);
        return (host == null || host.isBlank() ? advertisedHost : host) + ":" + port;
    }

    private String host(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        try {
            return URI.create(baseUrl.trim()).getHost();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}
