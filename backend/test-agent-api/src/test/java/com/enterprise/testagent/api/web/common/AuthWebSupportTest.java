package com.enterprise.testagent.api.web.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
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

    @Test
    void appliesSystemAdministratorHierarchy() {
        AuthPrincipal systemAdmin = principal(List.of(Dictionary.ROLE_SYSTEM_ADMIN));
        AuthPrincipal superAdmin = principal(List.of(Dictionary.ROLE_SUPER_ADMIN));

        assertThat(AuthWebSupport.hasRole(systemAdmin, Dictionary.ROLE_APP_ADMIN)).isTrue();
        assertThat(AuthWebSupport.hasRole(systemAdmin, Dictionary.ROLE_SUPER_ADMIN)).isFalse();
        assertThat(AuthWebSupport.hasRole(superAdmin, Dictionary.ROLE_SYSTEM_ADMIN)).isTrue();
    }

    private static AuthPrincipal principal() {
        return principal(List.of());
    }

    private static AuthPrincipal principal(List<String> roles) {
        return new AuthPrincipal(
                "token-123",
                new UserId("usr_1234567890abcdef"),
                "alice",
                "u123",
                roles,
                Instant.parse("2026-06-19T00:00:00Z"),
                Instant.parse("2026-06-20T00:00:00Z"));
    }
}
