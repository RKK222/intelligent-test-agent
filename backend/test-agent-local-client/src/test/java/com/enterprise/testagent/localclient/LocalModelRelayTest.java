package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalModelRelayTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldRequireLocalTokenAndOnlyForwardCurrentGrant() throws Exception {
        AtomicReference<String> receivedAuthorization = new AtomicReference<>();
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
        upstream.createContext("/api/internal/platform/local-opencode-client/model-proxy/v1", exchange -> {
            receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "proxied".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
        LocalClientConfiguration configuration = new LocalClientConfiguration(
                URI.create("http://127.0.0.1:" + upstream.getAddress().getPort()),
                URI.create("http://127.0.0.1:" + upstream.getAddress().getPort()),
                "test-client",
                temporaryDirectory.resolve("opencode"),
                temporaryDirectory.resolve("config"),
                temporaryDirectory.resolve("data"),
                4096,
                4097,
                true);
        try (LocalModelRelay relay = new LocalModelRelay(configuration)) {
            HttpClient client = HttpClient.newHttpClient();
            assertThat(send(client, relay.baseUrl() + "/chat/completions", null).statusCode()).isEqualTo(401);
            assertThat(send(client, relay.baseUrl() + "/chat/completions", relay.localToken()).statusCode())
                    .isEqualTo(503);

            relay.updateGrant("lcg_v1_current");
            HttpResponse<String> response = send(
                    client, relay.baseUrl() + "/chat/completions?stream=true", relay.localToken());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("proxied");
            assertThat(receivedAuthorization.get()).isEqualTo("Bearer lcg_v1_current");

            relay.clearGrant();
            assertThat(send(client, relay.baseUrl() + "/chat/completions", relay.localToken()).statusCode())
                    .isEqualTo(503);
        } finally {
            upstream.stop(0);
        }
    }

    private static HttpResponse<String> send(HttpClient client, String uri, String localToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri)).GET();
        if (localToken != null) {
            request.header("Authorization", "Bearer " + localToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
