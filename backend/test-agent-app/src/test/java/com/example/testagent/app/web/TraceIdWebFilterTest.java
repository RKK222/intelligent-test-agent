package com.example.testagent.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testagent.observability.TraceConstants;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class TraceIdWebFilterTest {

    @Test
    void filterPropagatesValidTraceIdToExchangeAndResponseHeader() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/health")
                .header(TraceConstants.TRACE_ID_HEADER, "trace_1234567890abcdef"));
        AtomicReference<String> attributeTraceId = new AtomicReference<>();
        WebFilterChain chain = currentExchange -> {
            attributeTraceId.set(currentExchange.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(attributeTraceId).hasValue("trace_1234567890abcdef");
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER))
                .isEqualTo("trace_1234567890abcdef");
    }

    @Test
    void filterReplacesInvalidTraceId() {
        TraceIdWebFilter filter = new TraceIdWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/health")
                .header(TraceConstants.TRACE_ID_HEADER, "bad trace id"));

        filter.filter(exchange, currentExchange -> Mono.empty()).block();

        String responseTraceId = exchange.getResponse().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        assertThat(responseTraceId).startsWith("trace_");
        assertThat(responseTraceId).isNotEqualTo("bad trace id");
    }
}
