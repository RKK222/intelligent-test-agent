package com.enterprise.testagent.model.gateway;

import com.enterprise.testagent.observability.TraceConstants;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** OpenAI-compatible 调用方共享的地址、可信请求头和安全响应头规则。 */
public final class OpenAiUpstreamSupport {

    public static final String PROVIDER_HEADER = "X-Enterprise-Model-Provider";
    public static final String UCID_HEADER = "ucid";

    private OpenAiUpstreamSupport() {
    }

    /**
     * 只写入服务端解析出的供应商密钥与用户身份；调用前主动清除同名客户端值，避免身份覆盖。
     */
    public static void applyTrustedRequestHeaders(
            HttpHeaders headers,
            String providerToken,
            String unifiedAuthId,
            String traceId,
            MediaType contentType,
            List<MediaType> accept) {
        Objects.requireNonNull(headers, "headers must not be null");
        headers.remove(PROVIDER_HEADER);
        headers.remove(HttpHeaders.AUTHORIZATION);
        headers.remove(UCID_HEADER);
        headers.remove(TraceConstants.TRACE_ID_HEADER);
        headers.setBearerAuth(requireText(providerToken, "providerToken"));
        headers.setContentType(Objects.requireNonNull(contentType, "contentType must not be null"));
        headers.setAccept(accept == null || accept.isEmpty()
                ? List.of(MediaType.APPLICATION_JSON)
                : List.copyOf(accept));
        if (unifiedAuthId != null && !unifiedAuthId.isBlank()) {
            headers.set(UCID_HEADER, unifiedAuthId.trim());
        }
        if (traceId != null && !traceId.isBlank()) {
            headers.set(TraceConstants.TRACE_ID_HEADER, traceId.trim());
        }
    }

    /** 仅转发与流式重试、缓存和诊断相关的白名单响应头。 */
    public static void copySafeResponseHeaders(
            HttpHeaders target,
            HttpHeaders source,
            boolean preserveContentEncoding) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(source, "source must not be null");
        MediaType contentType = source.getContentType();
        if (contentType != null) {
            target.setContentType(contentType);
        }
        if (preserveContentEncoding) {
            copyHeader(target, source, HttpHeaders.CONTENT_ENCODING);
        }
        copyHeader(target, source, HttpHeaders.RETRY_AFTER);
        copyHeader(target, source, TraceConstants.TRACE_ID_HEADER);
        copyHeader(target, source, HttpHeaders.CACHE_CONTROL);
    }

    /** 统一处理基础地址末尾斜杠、端点前导斜杠和兼容代理查询参数。 */
    public static String targetUrl(String baseUrl, String path, String rawQuery) {
        String normalizedBase = requireText(baseUrl, "baseUrl");
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        String normalizedPath = requireText(path, "path");
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return rawQuery == null || rawQuery.isBlank()
                ? normalizedBase + normalizedPath
                : normalizedBase + normalizedPath + "?" + rawQuery;
    }

    private static void copyHeader(HttpHeaders target, HttpHeaders source, String headerName) {
        List<String> values = source.get(headerName);
        if (values != null && !values.isEmpty()) {
            target.put(headerName, List.copyOf(values));
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
