package com.icbc.testagent.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

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
                .header("Access-Control-Request-Method", "POST"));

        filter.filter(exchange, chain -> reactor.core.publisher.Mono.empty()).block(java.time.Duration.ofSeconds(2));

        org.springframework.http.HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("Access-Control-Allow-Origin")).isEqualTo("http://127.0.0.1:4187");
        assertThat(headers.getFirst("Access-Control-Allow-Methods")).contains("POST");
    }
}
