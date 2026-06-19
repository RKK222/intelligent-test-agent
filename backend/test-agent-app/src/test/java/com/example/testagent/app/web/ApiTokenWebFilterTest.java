package com.example.testagent.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testagent.observability.TraceConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class ApiTokenWebFilterTest {

    @Test
    void filterAllowsRequestsWhenTokenIsNotConfigured() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter(null);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/workspaces"));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_1234567890abcdef");
        final boolean[] called = {false};
        WebFilterChain chain = currentExchange -> {
            called[0] = true;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(called[0]).isTrue();
    }

    @Test
    void filterRejectsRequestsWhenBearerTokenDoesNotMatch() {
        ApiTokenWebFilter filter = new ApiTokenWebFilter("secret-token");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/workspaces"));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_1234567890abcdef");

        filter.filter(exchange, currentExchange -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }
}
