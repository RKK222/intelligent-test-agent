package com.icbc.testagent.api.web.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class AuthWebSupportTest {

    @Test
    void optionalAuthPrincipalReturnsPrincipalWhenExchangeHasAuthenticatedUser() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/internal/platform/opencode-runtime/status"));
        AuthPrincipal principal = principal();
        exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);

        assertThat(AuthWebSupport.getOptionalAuthPrincipal(exchange)).contains(principal);
    }

    @Test
    void optionalAuthPrincipalReturnsEmptyWhenExchangeHasNoAuthenticatedUser() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/internal/platform/opencode-runtime/status"));

        assertThat(AuthWebSupport.getOptionalAuthPrincipal(exchange)).isEmpty();
    }

    private static AuthPrincipal principal() {
        return new AuthPrincipal(
                "token-123",
                new UserId("usr_1234567890abcdef"),
                "alice",
                "u123",
                List.of(),
                Instant.parse("2026-06-19T00:00:00Z"),
                Instant.parse("2026-06-20T00:00:00Z"));
    }
}
