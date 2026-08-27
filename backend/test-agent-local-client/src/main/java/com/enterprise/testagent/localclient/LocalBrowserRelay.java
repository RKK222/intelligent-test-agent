package com.enterprise.testagent.localclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 只供本地 OpenCode Tool 使用的浏览器生命周期控制面。 */
final class LocalBrowserRelay implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalBrowserRelay.class);
    private final LocalBrowserSupervisor supervisor;
    private final ObjectMapper objectMapper;
    private final HttpServer server;
    private final ExecutorService executor;
    private final String token = generateToken();

    LocalBrowserRelay(LocalBrowserSupervisor supervisor, ObjectMapper objectMapper) throws IOException {
        this.supervisor = supervisor;
        this.objectMapper = objectMapper;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/v1/status", exchange -> handle(exchange, "GET", supervisor::status));
        server.createContext("/v1/start", exchange -> handle(exchange, "POST", supervisor::start));
        server.createContext("/v1/stop", exchange -> handle(exchange, "POST", supervisor::stop));
        server.start();
        LOGGER.info("local_browser_relay_started port={}", server.getAddress().getPort());
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    String localToken() {
        return token;
    }

    LocalBrowserSupervisor.BrowserStatus stopBrowser() {
        return supervisor.stop();
    }

    LocalBrowserSupervisor.BrowserStatus startBrowser() {
        return supervisor.start();
    }

    private void handle(HttpExchange exchange, String method, Operation operation) throws IOException {
        try (exchange) {
            if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
                write(exchange, 405, java.util.Map.of("success", false, "code", "METHOD_NOT_ALLOWED"));
                return;
            }
            if (!authMatches(exchange.getRequestHeaders().getFirst("Authorization"))) {
                write(exchange, 401, java.util.Map.of("success", false, "code", "LOCAL_TOKEN_INVALID"));
                return;
            }
            try {
                write(exchange, 200, java.util.Map.of("success", true, "data", operation.run()));
            } catch (RuntimeException exception) {
                LOGGER.warn("local_browser_relay_operation_failed path={} rootFailureType={}",
                        exchange.getRequestURI().getPath(), LocalClientDiagnostics.rootFailureType(exception));
                write(exchange, 409, java.util.Map.of(
                        "success", false,
                        "code", "LOCAL_BROWSER_UNAVAILABLE",
                        "message", "本地 360 浏览器不可用，请在客户端托盘执行浏览器自检"));
            }
        }
    }

    private boolean authMatches(String authorization) {
        byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private void write(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static String generateToken() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return "browser_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
        supervisor.close();
        LOGGER.info("local_browser_relay_stopped");
    }

    @FunctionalInterface
    private interface Operation {
        LocalBrowserSupervisor.BrowserStatus run();
    }
}
