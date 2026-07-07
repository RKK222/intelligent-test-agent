package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessWeakHealthResponse;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessWeakHealthStatus;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.icbc.testagent.workspace.WorkspaceServerIdentity;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 当前用户 opencode 弱健康检查跨 Java 路由过滤器。
 *
 * <p>该接口不能依赖用户 binding 数据库路由，只使用前端传入的 linuxServerId 和 Redis 后端快照；
 * 目标为本服务器时放行到本地 Controller，目标为远端服务器时随机选择该服务器任一在线 Java 转发。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 24)
class UserOpencodeWeakHealthRoutingWebFilter implements WebFilter {

    private static final String HEALTH_PATH = "/api/internal/agent/opencode/processes/me/health";
    private static final int BACKEND_SNAPSHOT_LIMIT = 500;

    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final ObjectMapper objectMapper;

    @Autowired
    UserOpencodeWeakHealthRoutingWebFilter(
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            ObjectMapper objectMapper) {
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    UserOpencodeWeakHealthRoutingWebFilter(
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this(testRouteResolver(serverIdentity, heartbeatStore), new BackendHttpForwarder(objectMapper, httpClient), objectMapper);
    }

    private static BackendJavaRouteResolver testRouteResolver(
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore) {
        return new BackendJavaRouteResolver(
                heartbeatStore,
                new ManagerControlSettings(
                        "",
                        "http://" + serverIdentity.linuxServerId() + ":8080",
                        new LinuxServerId(serverIdentity.linuxServerId()),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(10),
                        100),
                java.time.Clock.systemUTC());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isWeakHealthRequest(exchange)) {
            return chain.filter(exchange);
        }
        String linuxServerId = firstQuery(exchange, "linuxServerId");
        if (linuxServerId.isBlank() || routeResolver.isCurrent(linuxServerId)) {
            return chain.filter(exchange);
        }
        if (exchange.getRequest().getHeaders().getFirst(BackendHttpForwarder.ROUTED_HEADER) != null) {
            return writeUnavailable(exchange, linuxServerId, "请求已转发但仍未到达目标服务器");
        }
        List<BackendJavaProcess> candidates = routeResolver.liveBackendSnapshots(BACKEND_SNAPSHOT_LIMIT).stream()
                .map(BackendRuntimeSnapshot::backendProcess)
                .filter(backend -> linuxServerId.equals(backend.linuxServerId().value()))
                .toList();
        if (candidates.isEmpty()) {
            return writeUnavailable(exchange, linuxServerId, "目标服务器后端不可用");
        }
        BackendJavaProcess backend = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        return forwarder.forwardRawResponse(exchange, backend)
                .flatMap((HttpResponse<byte[]> response) -> forwarder.writeRawResponse(exchange, response))
                .onErrorResume(exception -> writeUnavailable(exchange, linuxServerId, "目标服务器后端不可用"));
    }

    private boolean isWeakHealthRequest(ServerWebExchange exchange) {
        return HttpMethod.GET.equals(exchange.getRequest().getMethod())
                && HEALTH_PATH.equals(exchange.getRequest().getURI().getRawPath());
    }

    private Mono<Void> writeUnavailable(ServerWebExchange exchange, String linuxServerId, String message) {
        String traceId = traceId(exchange);
        String containerId = firstQuery(exchange, "containerId");
        int port = parsePort(firstQuery(exchange, "port"));
        try {
            byte[] body = objectMapper.writeValueAsBytes(ApiResponse.ok(
                    RuntimeDtos.UserOpencodeProcessHealthResponse.from(new OpencodeProcessWeakHealthResponse(
                            false,
                            OpencodeProcessWeakHealthStatus.BACKEND_UNAVAILABLE,
                            "NOT_RUNNING",
                            linuxServerId,
                            containerId,
                            port,
                            null,
                            Instant.now(),
                            message)),
                    traceId));
            exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(200));
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }

    private String firstQuery(ServerWebExchange exchange, String name) {
        String value = exchange.getRequest().getQueryParams().getFirst(name);
        return value == null ? "" : value.trim();
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String traceId(ServerWebExchange exchange) {
        String traceId = exchange.getResponse().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        }
        return traceId == null || traceId.isBlank() ? "trace_opencode_weak_health_route" : traceId;
    }
}
