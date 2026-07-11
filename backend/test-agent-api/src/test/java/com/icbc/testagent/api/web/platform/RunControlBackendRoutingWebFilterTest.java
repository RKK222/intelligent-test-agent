package com.icbc.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.opencode.runtime.run.RunEventSseRouteService;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class RunControlBackendRoutingWebFilterTest {

    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private static final String RUN_ID = "run_1234567890abcdef";

    @Test
    void forwardsAgentScopedCancelToProductionBackend() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        BackendJavaProcess target = backend();
        when(routeService.forwardTargetStrict(new RunId(RUN_ID))).thenReturn(Optional.of(target));
        when(forwarder.forwardRaw(any(), eq(target))).thenReturn(Mono.empty());
        RunControlBackendRoutingWebFilter filter = new RunControlBackendRoutingWebFilter(
                routeService, forwarder, errorWriter());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/agent/opencode/runs/" + RUN_ID + "/cancel?source=toolbar")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        verify(routeService).forwardTargetStrict(new RunId(RUN_ID));
        verify(forwarder).forwardRaw(exchange, target);
    }

    @Test
    void forwardsPlatformCancelToProductionBackend() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        BackendJavaProcess target = backend();
        when(routeService.forwardTargetStrict(new RunId(RUN_ID))).thenReturn(Optional.of(target));
        when(forwarder.forwardRaw(any(), eq(target))).thenReturn(Mono.empty());
        RunControlBackendRoutingWebFilter filter = new RunControlBackendRoutingWebFilter(
                routeService, forwarder, errorWriter());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/platform/opencode-runtime/runs/" + RUN_ID + "/cancel")
                .build());

        filter.filter(exchange, chain(exchange1 -> Mono.empty())).block(Duration.ofSeconds(2));

        verify(routeService).forwardTargetStrict(new RunId(RUN_ID));
        verify(forwarder).forwardRaw(exchange, target);
    }

    @Test
    void localOwnerContinuesToController() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        when(routeService.forwardTargetStrict(new RunId(RUN_ID))).thenReturn(Optional.empty());
        RunControlBackendRoutingWebFilter filter = new RunControlBackendRoutingWebFilter(
                routeService, forwarder, errorWriter());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/agent/opencode/runs/" + RUN_ID + "/cancel")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        verifyNoInteractions(forwarder);
    }

    @Test
    void clientControlledRoutedHeaderCannotBypassStrictCancelRouting() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        when(routeService.forwardTargetStrict(new RunId(RUN_ID))).thenReturn(Optional.empty());
        RunControlBackendRoutingWebFilter filter = new RunControlBackendRoutingWebFilter(
                routeService, forwarder, errorWriter());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/platform/opencode-runtime/runs/" + RUN_ID + "/cancel")
                .header(BackendHttpForwarder.ROUTED_HEADER, "true")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        verify(routeService).forwardTargetStrict(new RunId(RUN_ID));
        verifyNoInteractions(forwarder);
    }

    @Test
    void routingFailureNeverFallsThroughToLocalCancel() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        PlatformException unavailable = new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "目标服务器后端不可用",
                Map.of("linuxServerId", "server-b"));
        when(routeService.forwardTargetStrict(new RunId(RUN_ID))).thenThrow(unavailable);
        RunControlBackendRoutingWebFilter filter = new RunControlBackendRoutingWebFilter(
                routeService, forwarder, errorWriter());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/agent/opencode/runs/" + RUN_ID + "/cancel")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"OPENCODE_UNAVAILABLE\"")
                .contains("\"linuxServerId\":\"server-b\"");
        verifyNoInteractions(forwarder);
    }

    @Test
    void forwardingFailureNeverFallsThroughToLocalCancel() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        BackendJavaProcess target = backend();
        when(routeService.forwardTargetStrict(new RunId(RUN_ID))).thenReturn(Optional.of(target));
        when(forwarder.forwardRaw(any(), eq(target))).thenReturn(Mono.error(new IOException("connection refused")));
        RunControlBackendRoutingWebFilter filter = new RunControlBackendRoutingWebFilter(
                routeService, forwarder, errorWriter());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/agent/opencode/runs/" + RUN_ID + "/cancel")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"OPENCODE_UNAVAILABLE\"")
                .contains("\"linuxServerId\":\"server-b\"")
                .doesNotContain("connection refused");
    }

    @Test
    void ignoresNonCancelRunPaths() {
        RunEventSseRouteService routeService = mock(RunEventSseRouteService.class);
        BackendHttpForwarder forwarder = mock(BackendHttpForwarder.class);
        RunControlBackendRoutingWebFilter filter = new RunControlBackendRoutingWebFilter(
                routeService, forwarder, errorWriter());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/internal/agent/opencode/runs/" + RUN_ID + "/diff/accept")
                .build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chain(exchange1 -> {
            chainCalled.set(true);
            return Mono.empty();
        })).block(Duration.ofSeconds(2));

        assertThat(chainCalled).isTrue();
        verifyNoInteractions(routeService, forwarder);
    }

    private static WebFilterChain chain(
            java.util.function.Function<org.springframework.web.server.ServerWebExchange, Mono<Void>> delegate) {
        return delegate::apply;
    }

    private static BackendRoutingErrorWriter errorWriter() {
        return new BackendRoutingErrorWriter(new ObjectMapper().findAndRegisterModules());
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
