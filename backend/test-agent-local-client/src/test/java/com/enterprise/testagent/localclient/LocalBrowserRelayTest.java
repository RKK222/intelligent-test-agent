package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalBrowserRelayTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesOnlyAuthenticatedLoopbackStatusWithoutCdpDetailsWhenStopped() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        LocalBrowserSettings settings = new LocalBrowserSettings(
                temporaryDirectory.resolve("config"), temporaryDirectory.resolve("state"));
        LocalBrowserSupervisor supervisor = new LocalBrowserSupervisor(settings, objectMapper);
        try (LocalBrowserRelay relay = new LocalBrowserRelay(supervisor, objectMapper)) {
            assertThat(URI.create(relay.baseUrl()).getHost()).isEqualTo("127.0.0.1");
            HttpClient client = HttpClient.newHttpClient();

            assertThat(send(client, relay.baseUrl() + "/status", null).statusCode()).isEqualTo(401);
            HttpResponse<String> response = send(
                    client, relay.baseUrl() + "/status", relay.localToken());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            JsonNode payload = objectMapper.readTree(response.body());
            assertThat(payload.path("success").asBoolean()).isTrue();
            assertThat(payload.path("data").path("status").asText()).isEqualTo("STOPPED");
            assertThat(payload.path("data").path("running").asBoolean()).isFalse();
            assertThat(payload.path("data").path("cdpEndpoint").isNull()).isTrue();
        }
    }

    private static HttpResponse<String> send(HttpClient client, String uri, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
