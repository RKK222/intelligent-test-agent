package com.enterprise.testagent.api.web.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenStore;
import com.enterprise.testagent.domain.user.UserId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class JwtAuthWebFilterTest {

    @Test
    void bothLoginEndpointsIgnoreExistingPlatformBearerToken() {
        TokenStore tokenStore = mock(TokenStore.class);
        JwtAuthWebFilter filter = new JwtAuthWebFilter(tokenStore, new ObjectMapper());

        for (String path : List.of("/api/auth/login", "/api/auth/login-by-unified-auth")) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer stale-platform-token"));
            boolean[] called = {false};

            filter.filter(exchange, current -> {
                called[0] = true;
                return reactor.core.publisher.Mono.empty();
            }).block();

            assertThat(called[0]).as(path).isTrue();
        }
        verify(tokenStore, never()).findByToken("stale-platform-token");
    }

    @Test
    void protectedAuthEndpointStillRejectsExpiredPlatformToken() {
        TokenStore tokenStore = mock(TokenStore.class);
        AuthPrincipal expired = new AuthPrincipal(
                "expired", new UserId("usr_001"), "user", "AUTH_001", List.of("USER"),
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        when(tokenStore.findByToken("expired")).thenReturn(Optional.of(expired));
        JwtAuthWebFilter filter = new JwtAuthWebFilter(tokenStore, new ObjectMapper());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/refresh")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired"));

        filter.filter(exchange, current -> reactor.core.publisher.Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        verify(tokenStore).delete("expired");
    }
}
