package com.enterprise.testagent.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.api.web.platform.PersonalWorkspaceRelocationTransferController;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;

class RuntimeSecurityConfigTest {

    @Test
    void corsAllowsFrontendOpencodeRealE2eOrigin() {
        RuntimeSecurityConfig config = new RuntimeSecurityConfig(
                "http://localhost:3000,http://127.0.0.1:4187");

        var source = config.corsConfigurationSource();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .options("/api/internal/platform/opencode-runtime/sessions")
                .header("Origin", "http://127.0.0.1:4187")
                .header("Access-Control-Request-Method", "POST"));

        assertThat(source.getCorsConfiguration(exchange).checkOrigin("http://127.0.0.1:4187"))
                .isEqualTo("http://127.0.0.1:4187");
    }

    @Test
    void corsWebFilterAppliesCorsHeaders() {
        RuntimeSecurityConfig config = new RuntimeSecurityConfig(
                "http://localhost:3000,http://127.0.0.1:4187");

        org.springframework.web.cors.reactive.CorsWebFilter filter = config.corsWebFilter();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .options("http://127.0.0.1:8080/api/internal/platform/opencode-runtime/sessions")
                .header("Origin", "http://127.0.0.1:4187")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Test-Agent-Linux-Server-Id,X-Support-Access-Grant"));

        filter.filter(exchange, chain -> reactor.core.publisher.Mono.empty()).block(java.time.Duration.ofSeconds(2));

        org.springframework.http.HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("Access-Control-Allow-Origin")).isEqualTo("http://127.0.0.1:4187");
        assertThat(headers.getFirst("Access-Control-Allow-Methods")).contains("POST");
        assertThat(headers.getFirst("Access-Control-Allow-Headers"))
                .containsIgnoringCase("X-Test-Agent-Linux-Server-Id");
        assertThat(headers.getFirst("Access-Control-Allow-Headers"))
                .containsIgnoringCase("X-Support-Access-Grant");
    }

    @Test
    void corsAllowsRelocationInternalOriginOnlyOnExactWebSocketPath() {
        RuntimeSecurityConfig config = new RuntimeSecurityConfig(
                "http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996");
        CorsWebFilter filter = config.corsWebFilter();
        AtomicBoolean exactPathInvoked = new AtomicBoolean();
        var exactPath = relocationExchange(
                PersonalWorkspaceRelocationTransferController.WEB_SOCKET_PATH,
                PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN);

        filter.filter(exactPath, chain -> {
                    exactPathInvoked.set(true);
                    return reactor.core.publisher.Mono.empty();
                })
                .block(Duration.ofSeconds(2));

        assertThat(exactPathInvoked).isTrue();
        assertThat(exactPath.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"))
                .isEqualTo(PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN);

        AtomicBoolean childPathInvoked = new AtomicBoolean();
        var childPath = relocationExchange(
                PersonalWorkspaceRelocationTransferController.WEB_SOCKET_PATH + "/child",
                PersonalWorkspaceRelocationTransferController.INTERNAL_ORIGIN);
        filter.filter(childPath, chain -> {
                    childPathInvoked.set(true);
                    return reactor.core.publisher.Mono.empty();
                })
                .block(Duration.ofSeconds(2));

        assertThat(childPathInvoked).isFalse();
        assertThat(childPath.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void corsRejectsBrowserOriginOnRelocationInternalWebSocketPath() {
        RuntimeSecurityConfig config = new RuntimeSecurityConfig(
                "http://mimo.sdc.cs.icbc:9996,http://122.233.30.2:9996");
        CorsWebFilter filter = config.corsWebFilter();
        AtomicBoolean invoked = new AtomicBoolean();
        var exchange = relocationExchange(
                PersonalWorkspaceRelocationTransferController.WEB_SOCKET_PATH,
                "http://mimo.sdc.cs.icbc:9996");

        filter.filter(exchange, chain -> {
                    invoked.set(true);
                    return reactor.core.publisher.Mono.empty();
                })
                .block(Duration.ofSeconds(2));

        assertThat(invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private MockServerWebExchange relocationExchange(String path, String origin) {
        return MockServerWebExchange.from(MockServerHttpRequest
                .get("http://122.233.30.114:8080" + path)
                .header("Origin", origin)
                .header("Connection", "Upgrade")
                .header("Upgrade", "websocket"));
    }
}
