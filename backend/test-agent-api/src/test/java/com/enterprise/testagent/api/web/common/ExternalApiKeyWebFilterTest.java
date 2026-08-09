package com.enterprise.testagent.api.web.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialId;
import com.enterprise.testagent.domain.externalapi.ExternalApiPrincipal;
import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.system.management.externalapi.ExternalApiCredentialRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/** 验证外部命名空间始终由专用 Header 认证，且失败响应不泄露工具或 Key。 */
class ExternalApiKeyWebFilterTest {

    @Test
    void validHeadersSetExternalPrincipal() {
        ExternalApiCredentialRegistry registry = mock(ExternalApiCredentialRegistry.class);
        ExternalApiPrincipal principal = principal();
        when(registry.authenticate("deploy.bot", API_KEY)).thenReturn(principal);
        ExternalApiKeyWebFilter filter = new ExternalApiKeyWebFilter(registry, new ObjectMapper());
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get(
                        "/api/external/v1/users/u001/ssh-key")
                .header("X-Test-Agent-Tool-Code", "deploy.bot")
                .header("X-Test-Agent-Api-Key", API_KEY));
        final boolean[] called = {false};

        filter.filter(exchange, current -> {
            called[0] = true;
            return Mono.empty();
        }).block();

        assertThat(called[0]).isTrue();
        assertThat(ExternalApiWebSupport.getPrincipal(exchange)).isEqualTo(principal);
    }

    @Test
    void bearerOrStaticTokenCannotReplaceExternalHeadersAndFailuresUseSame401Body() {
        ExternalApiCredentialRegistry registry = mock(ExternalApiCredentialRegistry.class);
        when(registry.authenticate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new PlatformException(ErrorCode.UNAUTHENTICATED, "internal detail"));
        ExternalApiKeyWebFilter filter = new ExternalApiKeyWebFilter(registry, new ObjectMapper());
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get(
                        "/api/external/v1/users/u001/ssh-key")
                .header("Authorization", "Bearer legacy-user-or-static-token"));

        filter.filter(exchange, current -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("UNAUTHENTICATED").doesNotContain("internal detail").doesNotContain(API_KEY);
    }

    @Test
    void unavailableRegistryReturnsStable503AndAdjacentPathIsIgnored() {
        ExternalApiCredentialRegistry registry = mock(ExternalApiCredentialRegistry.class);
        when(registry.authenticate("deploy.bot", API_KEY))
                .thenThrow(new PlatformException(ErrorCode.EXTERNAL_API_UNAVAILABLE, "database detail"));
        ExternalApiKeyWebFilter filter = new ExternalApiKeyWebFilter(registry, new ObjectMapper());
        MockServerWebExchange unavailable = exchange(MockServerHttpRequest.get(
                        "/api/external/v1/users/u001/ssh-key")
                .header("X-Test-Agent-Tool-Code", "deploy.bot")
                .header("X-Test-Agent-Api-Key", API_KEY));

        filter.filter(unavailable, current -> Mono.empty()).block();

        assertThat(unavailable.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(unavailable.getResponse().getBodyAsString().block())
                .contains("EXTERNAL_API_UNAVAILABLE").doesNotContain("database detail");

        MockServerWebExchange adjacent = exchange(MockServerHttpRequest.get("/api/external/v10/test"));
        final boolean[] called = {false};
        filter.filter(adjacent, current -> {
            called[0] = true;
            return Mono.empty();
        }).block();
        assertThat(called[0]).isTrue();
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_1234567890abcdef");
        return exchange;
    }

    private static ExternalApiPrincipal principal() {
        return new ExternalApiPrincipal(
                new ExternalApiCredentialId("eac_one"), "deploy.bot",
                Set.of(ExternalApiScope.USER_SSH_KEY_READ), API_KEY);
    }

    private static final String API_KEY = "taak_v1_AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";
}
