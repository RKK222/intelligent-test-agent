package com.example.testagent.api.web.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testagent.observability.TraceConstants;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class InMemoryRateLimitWebFilterTest {

    @Test
    void filterReturnsRateLimitedAfterCapacityIsConsumedInWindow() {
        InMemoryRateLimitWebFilter filter = new InMemoryRateLimitWebFilter(true, 1, Duration.ofMinutes(1));
        WebFilterChain chain = exchange -> Mono.empty();
        MockServerWebExchange first = exchange();
        MockServerWebExchange second = exchange();

        filter.filter(first, chain).block();
        filter.filter(second, chain).block();

        assertThat(first.getResponse().getStatusCode()).isNull();
        assertThat(second.getResponse().getStatusCode().value()).isEqualTo(429);
    }

    private static MockServerWebExchange exchange() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/runs"));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_1234567890abcdef");
        return exchange;
    }
}
