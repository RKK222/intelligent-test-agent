package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.opencode.runtime.run.RunEventSseRouteService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class RunEventSseBackendRoutingWebFilterTest {

    private static final Instant NOW = Instant.parse("2026-07-09T00:00:00Z");
    private static final String RUN_ID = "run_1234567890abcdef";

    @Test
    void forwardsAgentScopedRunEventSseToProductionBackend() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendSseForwarder forwarder = mock(BackendSseForwarder.class);
        BackendJavaProcess target = backend();
        when(routeService.forwardTarget(new RunId(RUN_ID))).thenReturn(Optional.of(target));
        when(forwarder.forward(org.mockito.ArgumentMatchers.any(), eq(target))).thenReturn(Mono.empty());
        RunEventSseBackendRoutingWebFilter filter = new RunEventSseBackendRoutingWebFilter(routeService, forwarder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/runs/" + RUN_ID + "/events?lastEventId=7")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .header(TraceConstants.TRACE_ID_HEADER, "trace_1234567890abcdef")
                .header("Last-Event-ID", "6")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        verify(routeService).forwardTarget(new RunId(RUN_ID));
        verify(forwarder).forward(exchange, target);
    }

    @Test
    void forwardsPlatformRunEventSseToProductionBackend() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendSseForwarder forwarder = mock(BackendSseForwarder.class);
        BackendJavaProcess target = backend();
        when(routeService.forwardTarget(new RunId(RUN_ID))).thenReturn(Optional.of(target));
        when(forwarder.forward(org.mockito.ArgumentMatchers.any(), eq(target))).thenReturn(Mono.empty());
        RunEventSseBackendRoutingWebFilter filter = new RunEventSseBackendRoutingWebFilter(routeService, forwarder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/platform/opencode-runtime/runs/" + RUN_ID + "/events")
                .build());

        filter.filter(exchange, chain(exchange1 -> Mono.empty())).block(Duration.ofSeconds(2));

        verify(routeService).forwardTarget(new RunId(RUN_ID));
        verify(forwarder).forward(exchange, target);
    }

    @Test
    void routedHeaderSkipsForwardingToAvoidLoops() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendSseForwarder forwarder = mock(BackendSseForwarder.class);
        RunEventSseBackendRoutingWebFilter filter = new RunEventSseBackendRoutingWebFilter(routeService, forwarder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/runs/" + RUN_ID + "/events")
                .header(BackendHttpForwarder.ROUTED_HEADER, "true")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        verifyNoInteractions(routeService, forwarder);
    }

    @Test
    void missingProductionBackendFallsThroughToLocalDbReplay() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendSseForwarder forwarder = mock(BackendSseForwarder.class);
        when(routeService.forwardTarget(new RunId(RUN_ID))).thenReturn(Optional.empty());
        RunEventSseBackendRoutingWebFilter filter = new RunEventSseBackendRoutingWebFilter(routeService, forwarder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/runs/" + RUN_ID + "/events")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        verify(routeService).forwardTarget(new RunId(RUN_ID));
        verifyNoInteractions(forwarder);
    }

    @Test
    void ignoresNonRunEventPaths() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendSseForwarder forwarder = mock(BackendSseForwarder.class);
        RunEventSseBackendRoutingWebFilter filter = new RunEventSseBackendRoutingWebFilter(routeService, forwarder);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/internal/agent/opencode/runs/" + RUN_ID + "/diff")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        verifyNoInteractions(routeService, forwarder);
    }

    private static WebFilterChain chain(java.util.function.Function<org.springframework.web.server.ServerWebExchange, Mono<Void>> delegate) {
        return delegate::apply;
    }

    private static BackendJavaProcess backend() {
        return new BackendJavaProcess(
                new BackendProcessId("bjp_1234567890abcdef"),
                new LinuxServerId("server-b"),
                "http://10.8.0.22:8080",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                "trace_backend");
    }
}
