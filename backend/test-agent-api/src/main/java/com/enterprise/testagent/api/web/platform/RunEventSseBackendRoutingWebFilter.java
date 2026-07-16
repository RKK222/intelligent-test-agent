package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.opencode.runtime.run.RunEventSseRouteService;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * RunEvent SSE 专用后端路由过滤器。
 *
 * <p>SSE 入口按 Run 原始 routing_decision 定位生产 Java。目标 Java 收到转发头后不再二次路由，
 * 继续执行现有 RunController，先输出消息 snapshot，再进入 durable replay 和本机 live bus。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 26)
class RunEventSseBackendRoutingWebFilter implements WebFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunEventSseBackendRoutingWebFilter.class);
    private static final String PLATFORM_RUN_EVENTS_PREFIX = "/api/internal/platform/opencode-runtime/runs/";
    private static final String AGENT_PREFIX = "/api/internal/agent/";
    private static final String RUNS_SEGMENT = "/runs/";
    private static final String EVENTS_SUFFIX = "/events";

    private final RunEventSseRouteService routeService;
    private final BackendSseForwarder forwarder;

    RunEventSseBackendRoutingWebFilter(RunEventSseRouteService routeService, BackendSseForwarder forwarder) {
        this.routeService = Objects.requireNonNull(routeService, "routeService must not be null");
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder must not be null");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Optional<RunId> runId = runId(exchange);
        if (runId.isEmpty()) {
            return chain.filter(exchange);
        }
        return Mono.fromCallable(() -> routeService.forwardTarget(runId.get()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(target -> target
                        .map(backend -> forwardOrFallback(exchange, chain, runId.get(), backend))
                        .orElseGet(() -> chain.filter(exchange)))
                .onErrorResume(PlatformException.class, exception -> fallbackIfPossible(exchange, chain, runId.get(), exception));
    }

    private Mono<Void> forwardOrFallback(
            ServerWebExchange exchange,
            WebFilterChain chain,
            RunId runId,
            BackendJavaProcess backend) {
        return forwarder.forward(exchange, backend)
                .onErrorResume(PlatformException.class, exception -> fallbackIfPossible(exchange, chain, runId, exception));
    }

    private Mono<Void> fallbackIfPossible(
            ServerWebExchange exchange,
            WebFilterChain chain,
            RunId runId,
            PlatformException exception) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }
        LOGGER.warn(
                "RunEvent SSE forward failed, falling back to local replay. runId={} errorCode={}",
                runId.value(),
                exception.errorCode());
        return chain.filter(exchange);
    }

    private Optional<RunId> runId(ServerWebExchange exchange) {
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod()) || isRouted(exchange)) {
            return Optional.empty();
        }
        return runIdFromPath(exchange.getRequest().getURI().getRawPath())
                .flatMap(this::parseRunId);
    }

    private Optional<String> runIdFromPath(String path) {
        Optional<String> platformRunId = runIdAfterPrefix(path, PLATFORM_RUN_EVENTS_PREFIX);
        if (platformRunId.isPresent()) {
            return platformRunId;
        }
        if (!path.startsWith(AGENT_PREFIX) || !path.endsWith(EVENTS_SUFFIX)) {
            return Optional.empty();
        }
        String suffix = path.substring(AGENT_PREFIX.length());
        int runsIndex = suffix.indexOf(RUNS_SEGMENT);
        if (runsIndex <= 0) {
            return Optional.empty();
        }
        return cleanRunId(suffix.substring(runsIndex + RUNS_SEGMENT.length(), suffix.length() - EVENTS_SUFFIX.length()));
    }

    private Optional<String> runIdAfterPrefix(String path, String prefix) {
        if (!path.startsWith(prefix) || !path.endsWith(EVENTS_SUFFIX)) {
            return Optional.empty();
        }
        return cleanRunId(path.substring(prefix.length(), path.length() - EVENTS_SUFFIX.length()));
    }

    private Optional<String> cleanRunId(String value) {
        return value == null || value.isBlank() || value.contains("/") ? Optional.empty() : Optional.of(value);
    }

    private Optional<RunId> parseRunId(String value) {
        try {
            return Optional.of(new RunId(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private boolean isRouted(ServerWebExchange exchange) {
        return "true".equalsIgnoreCase(exchange.getRequest().getHeaders().getFirst(BackendHttpForwarder.ROUTED_HEADER));
    }
}
