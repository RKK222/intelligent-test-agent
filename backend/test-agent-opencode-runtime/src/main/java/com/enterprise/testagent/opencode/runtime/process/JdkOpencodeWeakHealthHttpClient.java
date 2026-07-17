package com.enterprise.testagent.opencode.runtime.process;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 使用 JDK HttpClient 直接调用 opencode /global/health 的弱健康检查实现。
 */
final class JdkOpencodeWeakHealthHttpClient implements OpencodeWeakHealthHttpClient {

    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final HttpClient httpClient;

    JdkOpencodeWeakHealthHttpClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(HEALTH_TIMEOUT)
                .build());
    }

    JdkOpencodeWeakHealthHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public OpencodeWeakHealthHttpResult check(String baseUrl, String traceId) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/global/health"))
                    .timeout(HEALTH_TIMEOUT)
                    .GET();
            if (traceId != null && !traceId.isBlank()) {
                builder.header(TRACE_ID_HEADER, traceId);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new OpencodeWeakHealthHttpResult(true, "ok");
            }
            return new OpencodeWeakHealthHttpResult(false, "HTTP " + response.statusCode());
        } catch (Exception exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "opencode 弱健康检查异常"
                    : exception.getMessage();
            return new OpencodeWeakHealthHttpResult(false, message);
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
