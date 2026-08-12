package com.enterprise.testagent.localclient;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** OpenCode 只访问此 loopback 中继并持有随机本地 token；平台模型 grant 由中继内存转发。 */
final class LocalModelRelay implements AutoCloseable {

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "cookie");

    private final LocalClientConfiguration configuration;
    private final HttpClient httpClient;
    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicReference<String> modelGrant = new AtomicReference<>();
    private final String localToken = generateToken();

    LocalModelRelay(LocalClientConfiguration configuration) throws IOException {
        this.configuration = configuration;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 64);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/v1", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    String localToken() {
        return localToken;
    }

    void updateGrant(String rawGrant) {
        if (rawGrant == null || !rawGrant.startsWith("lcg_v1_")) {
            throw new IllegalArgumentException("model grant is invalid");
        }
        modelGrant.set(rawGrant);
    }

    void clearGrant() {
        modelGrant.set(null);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!localAuthMatches(exchange.getRequestHeaders().getFirst("Authorization"))) {
                writeError(exchange, 401, "local model token invalid");
                return;
            }
            String grant = modelGrant.get();
            if (grant == null) {
                writeError(exchange, 503, "model grant unavailable while client is disconnected");
                return;
            }
            String rawPath = exchange.getRequestURI().getRawPath();
            String suffix = rawPath.length() <= 3 ? "/" : rawPath.substring(3);
            if (exchange.getRequestURI().getRawQuery() != null) {
                suffix += "?" + exchange.getRequestURI().getRawQuery();
            }
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(configuration.modelProxyUri(suffix))
                    .timeout(Duration.ofHours(24))
                    .header("Authorization", "Bearer " + grant);
            copyRequestHeaders(exchange.getRequestHeaders(), request);
            HttpRequest.BodyPublisher body = requestBody(exchange);
            HttpResponse<InputStream> response = httpClient.send(
                    request.method(exchange.getRequestMethod(), body).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            copyResponseHeaders(response, exchange.getResponseHeaders());
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(0L);
            exchange.sendResponseHeaders(response.statusCode(), contentLength);
            try (InputStream input = response.body()) {
                input.transferTo(exchange.getResponseBody());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writeErrorIfPossible(exchange, 503, "model relay interrupted");
        } catch (Exception exception) {
            writeErrorIfPossible(exchange, 502, "model relay upstream failed");
        }
    }

    private static HttpRequest.BodyPublisher requestBody(HttpExchange exchange) {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())
                || "HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofInputStream(exchange::getRequestBody);
    }

    private static void copyRequestHeaders(Headers source, HttpRequest.Builder target) {
        source.forEach((name, values) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP.contains(normalized) || "authorization".equals(normalized) || "content-length".equals(normalized)) {
                return;
            }
            for (String value : values) {
                target.header(name, value);
            }
        });
    }

    private static void copyResponseHeaders(HttpResponse<?> source, Headers target) {
        source.headers().map().forEach((name, values) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP.contains(normalized) || "content-length".equals(normalized)) {
                return;
            }
            target.put(name, List.copyOf(values));
        });
    }

    private boolean localAuthMatches(String authorization) {
        return ("Bearer " + localToken).equals(authorization);
    }

    private static void writeError(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void writeErrorIfPossible(HttpExchange exchange, int status, String message) {
        try {
            writeError(exchange, status, message);
        } catch (IOException ignored) {
            // 响应头已发送或连接已关闭时没有更多可写内容。
        }
    }

    private static String generateToken() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return "local_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
