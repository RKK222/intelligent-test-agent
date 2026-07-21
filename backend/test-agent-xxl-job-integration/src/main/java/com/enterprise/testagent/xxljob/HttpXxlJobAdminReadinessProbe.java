package com.enterprise.testagent.xxljob;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * 通过 Admin 自身的 readiness 端点判断 MySQL、Flyway 与 Servlet 上下文是否已可用。
 * 探测不携带 access token，也不读取响应正文，避免敏感信息进入平台进程。
 */
final class HttpXxlJobAdminReadinessProbe implements XxlJobAdminReadinessProbe {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(1);

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    HttpXxlJobAdminReadinessProbe() {
        this(DEFAULT_TIMEOUT);
    }

    HttpXxlJobAdminReadinessProbe(Duration requestTimeout) {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public boolean isAnyReady(String adminAddresses) {
        if (adminAddresses == null || adminAddresses.isBlank()) {
            return false;
        }
        String[] addresses = adminAddresses.split(",");
        for (String address : addresses) {
            Optional<URI> readinessUri = readinessUri(address);
            if (readinessUri.isPresent() && isReady(readinessUri.orElseThrow())) {
                return true;
            }
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
        }
        return false;
    }

    private boolean isReady(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException exception) {
            // Admin 尚未监听、网络暂不可达和非法地址都属于可恢复的未就绪状态，不在轮询热路径记录堆栈。
            return false;
        }
    }

    private static Optional<URI> readinessUri(String configuredAddress) {
        if (configuredAddress == null || configuredAddress.isBlank()) {
            return Optional.empty();
        }
        try {
            String address = configuredAddress.trim();
            URI baseUri = URI.create(address);
            String scheme = baseUri.getScheme();
            if (scheme == null
                    || baseUri.getHost() == null
                    || baseUri.getUserInfo() != null
                    || baseUri.getQuery() != null
                    || baseUri.getFragment() != null) {
                return Optional.empty();
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return Optional.empty();
            }
            URI directory = URI.create(address.endsWith("/") ? address : address + "/");
            return Optional.of(directory.resolve("actuator/health/readiness"));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
