package com.enterprise.testagent.api.web.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.observability.TraceConstants;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class LegacyApiGoneWebFilterTest {

    private final LegacyApiGoneWebFilter filter = new LegacyApiGoneWebFilter(new ObjectMapper());

    @Test
    void rejectsLegacyApiEntrypointsWithGoneResponse() {
        List<LegacyRequest> requests = List.of(
                new LegacyRequest(HttpMethod.POST, "/api/runs"),
                new LegacyRequest(HttpMethod.GET, "/api/runs/run_1234567890abcdef/events"),
                new LegacyRequest(HttpMethod.GET, "/api/sessions/ses_1234567890abcdef/messages"),
                new LegacyRequest(HttpMethod.GET, "/api/agents"),
                new LegacyRequest(HttpMethod.POST, "/api/sessions/ses_1234567890abcdef/terminal/tickets"),
                new LegacyRequest(HttpMethod.POST, "/api/workspaces/wrk_1234567890abcdef/file-ws-route"),
                new LegacyRequest(HttpMethod.GET, "/api/internal/platform/workspace-management/workspaces/wrk_1234567890abcdef/files"),
                new LegacyRequest(HttpMethod.GET, "/api/internal/platform/workspace-management/agent-config/public/files"),
                new LegacyRequest(HttpMethod.GET, "/api/internal/platform/opencode-runtime/manager-backends"),
                new LegacyRequest(HttpMethod.GET, "/api/internal/platform/opencode-runtime/management/backend-processes/bjp_1234567890abcdef/metrics"));

        for (LegacyRequest request : requests) {
            MockServerWebExchange exchange = exchange(request.method(), request.path());
            final boolean[] called = {false};

            filter.filter(exchange, chain(called)).block();

            assertThat(called[0]).as(request.path()).isFalse();
            assertThat(exchange.getResponse().getStatusCode().value()).as(request.path()).isEqualTo(410);
            String body = exchange.getResponse().getBodyAsString().block();
            assertThat(body).contains("\"code\":\"API_GONE\"");
            assertThat(body).contains("\"traceId\":\"trace_1234567890abcdef\"");
        }
    }

    @Test
    void allowsNewEntrypointsAndStableAuthApis() {
        List<LegacyRequest> requests = List.of(
                new LegacyRequest(HttpMethod.POST, "/api/internal/agent/opencode/runs"),
                new LegacyRequest(HttpMethod.GET, "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/messages"),
                new LegacyRequest(HttpMethod.POST, "/api/internal/platform/opencode-runtime/sessions/ses_1234567890abcdef/terminal/tickets"),
                new LegacyRequest(HttpMethod.POST, "/api/internal/platform/workspace-management/workspaces/wrk_1234567890abcdef/file-ws-route"),
                new LegacyRequest(HttpMethod.POST, "/api/auth/login"),
                new LegacyRequest(HttpMethod.GET, "/api/auth/me"));

        for (LegacyRequest request : requests) {
            MockServerWebExchange exchange = exchange(request.method(), request.path());
            final boolean[] called = {false};

            filter.filter(exchange, chain(called)).block();

            assertThat(called[0]).as(request.path()).isTrue();
            assertThat(exchange.getResponse().getStatusCode()).as(request.path()).isNull();
        }
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_1234567890abcdef");
        return exchange;
    }

    private static WebFilterChain chain(boolean[] called) {
        return currentExchange -> {
            called[0] = true;
            return Mono.empty();
        };
    }

    private record LegacyRequest(HttpMethod method, String path) {
    }
}
