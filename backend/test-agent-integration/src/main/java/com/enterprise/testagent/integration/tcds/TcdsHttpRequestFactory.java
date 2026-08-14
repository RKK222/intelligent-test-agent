package com.enterprise.testagent.integration.tcds;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** TCDS 请求构造单一入口，统一基础地址、超时和同源 toolId 注入规则。 */
final class TcdsHttpRequestFactory {

    private static final String TOOL_ID_HEADER = "toolId";
    private static final String TOOL_ID = "66f36bfa5c1c6105572b0118880261d6";

    private final URI baseUri;

    TcdsHttpRequestFactory(TcdsProperties properties) {
        this.baseUri = requireBaseUri(properties == null ? null : properties.getBaseUrl());
    }

    /** 相对路径始终解析到部署配置的 TCDS 基础地址，避免业务服务维护并行生产地址。 */
    URI resolve(String pathAndQuery) {
        String normalized = Objects.requireNonNull(pathAndQuery, "pathAndQuery must not be null");
        return baseUri.resolve(normalized.startsWith("/") ? normalized.substring(1) : normalized);
    }

    HttpRequest.Builder request(String pathAndQuery, Duration timeout) {
        return request(resolve(pathAndQuery), timeout);
    }

    /** 只向 TCDS 同源请求注入 toolId，跨域对象存储下载不透传内部 header。 */
    HttpRequest.Builder request(URI uri, Duration timeout) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Objects.requireNonNull(timeout, "timeout must not be null"));
        if (sameOrigin(baseUri, uri)) {
            request.header(TOOL_ID_HEADER, TOOL_ID);
        }
        return request;
    }

    private static URI requireBaseUri(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("TEST_AGENT_TCDS_BASE_URL 未配置");
        }
        try {
            URI uri = URI.create(raw.trim());
            requireHttpUri(uri);
            String normalized = uri.toString().endsWith("/") ? uri.toString() : uri + "/";
            return URI.create(normalized);
        } catch (PlatformException | IllegalArgumentException exception) {
            throw new IllegalStateException("TEST_AGENT_TCDS_BASE_URL 配置无效");
        }
    }

    private static void requireHttpUri(URI uri) {
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "TCDS 基础地址无效");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "TCDS 基础地址无效");
        }
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
