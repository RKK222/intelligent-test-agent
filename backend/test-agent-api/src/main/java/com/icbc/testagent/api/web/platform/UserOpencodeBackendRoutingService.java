package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.api.ApiErrorResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.workspace.WorkspaceServerIdentity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 用户 opencode 进程跨后端路由服务。
 *
 * <p>当前 Java 只控制本服务器 manager；如果用户 ACTIVE binding 属于其他服务器，
 * API 层把相关 HTTP 请求转发到 binding 所属服务器的 Java，由目标 Java 继续鉴权并操作本机 manager。
 */
@Service
class UserOpencodeBackendRoutingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserOpencodeBackendRoutingService.class);
    private static final Duration FORWARD_TIMEOUT = Duration.ofSeconds(30);
    private static final String OPENCODE_AGENT_ID = "opencode";
    private static final String AGENT_PREFIX = "/api/internal/agent/";
    private static final String PLATFORM_RUNTIME_PREFIX = "/api/internal/platform/opencode-runtime";
    private static final List<String> LEGACY_RUNTIME_PREFIXES = List.of(
            "/api/agents",
            "/api/models",
            "/api/providers",
            "/api/commands",
            "/api/references",
            "/api/status",
            "/api/fs/",
            "/api/vcs/",
            "/api/lsp/",
            "/api/mcp/",
            "/api/config",
            "/api/global/",
            "/api/provider/",
            "/api/worktrees",
            "/api/sessions");
    private static final List<String> FORWARDED_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.ACCEPT,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT_LANGUAGE,
            TraceConstants.TRACE_ID_HEADER);

    private final UserOpencodeProcessAssignmentService assignmentService;
    private final WorkspaceServerIdentity serverIdentity;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    UserOpencodeBackendRoutingService(
            UserOpencodeProcessAssignmentService assignmentService,
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper) {
        this(assignmentService, serverIdentity, heartbeatStore, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    UserOpencodeBackendRoutingService(
            UserOpencodeProcessAssignmentService assignmentService,
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    /**
     * 解析当前请求是否需要路由到远端 binding 所属后端。
     */
    Optional<String> targetLinuxServerId(ServerWebExchange exchange, AuthPrincipal principal) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        if (exchange.getRequest().getHeaders().getFirst(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER) != null) {
            return Optional.empty();
        }
        Optional<String> agentId = routeAgentId(exchange);
        if (agentId.isEmpty()) {
            return Optional.empty();
        }
        return assignmentService.routingLinuxServerId(principal.userId(), agentId.get())
                .filter(linuxServerId -> !serverIdentity.linuxServerId().equals(linuxServerId));
    }

    /**
     * 转发原始 HTTP 请求，并把目标 Java 的响应原样写回当前响应。
     */
    Mono<Void> forward(ServerWebExchange exchange, String linuxServerId) {
        BackendJavaProcess backend = backendFor(linuxServerId);
        if (backend == null) {
            return writeError(
                    exchange,
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "目标服务器后端不可用",
                    Map.of("linuxServerId", linuxServerId));
        }
        return requestBody(exchange)
                .flatMap(body -> Mono.fromCallable(() -> send(exchange, backend, body))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(response -> writeForwardResponse(exchange, response))
                .onErrorResume(exception -> {
                    LOGGER.warn("用户 opencode 请求转发失败 linuxServerId={} traceId={}",
                            linuxServerId, traceId(exchange), exception);
                    return writeError(
                            exchange,
                            ErrorCode.OPENCODE_UNAVAILABLE,
                            "目标服务器后端不可用",
                            Map.of("linuxServerId", linuxServerId));
                });
    }

    private Optional<String> routeAgentId(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        HttpMethod method = exchange.getRequest().getMethod();
        if (path == null || method == null) {
            return Optional.empty();
        }
        Optional<String> agentPathAgentId = agentPathAgentId(path, method);
        if (agentPathAgentId.isPresent()) {
            return agentPathAgentId;
        }
        if (isPlatformRuntimePath(path, method) || isLegacyRuntimePath(path, method)) {
            return Optional.of(OPENCODE_AGENT_ID);
        }
        return Optional.empty();
    }

    private Optional<String> agentPathAgentId(String path, HttpMethod method) {
        if (!path.startsWith(AGENT_PREFIX)) {
            return Optional.empty();
        }
        String rest = path.substring(AGENT_PREFIX.length());
        int slashIndex = rest.indexOf('/');
        if (slashIndex <= 0) {
            return Optional.empty();
        }
        String agentId = rest.substring(0, slashIndex);
        if (!OPENCODE_AGENT_ID.equals(agentId.trim().toLowerCase())) {
            return Optional.empty();
        }
        String suffix = rest.substring(slashIndex);
        if ("/runs".equals(suffix)) {
            return HttpMethod.POST.equals(method) ? Optional.of(agentId) : Optional.empty();
        }
        if (suffix.startsWith("/runs/")) {
            return Optional.empty();
        }
        return Optional.of(agentId);
    }

    private boolean isPlatformRuntimePath(String path, HttpMethod method) {
        if (!path.startsWith(PLATFORM_RUNTIME_PREFIX)) {
            return false;
        }
        String suffix = path.substring(PLATFORM_RUNTIME_PREFIX.length());
        if (suffix.isEmpty() || suffix.equals("/")) {
            return false;
        }
        if (suffix.startsWith("/management")
                || suffix.startsWith("/manager")
                || suffix.startsWith("/messages")) {
            return false;
        }
        if ("/runs".equals(suffix)) {
            return HttpMethod.POST.equals(method);
        }
        return !suffix.startsWith("/runs/");
    }

    private boolean isLegacyRuntimePath(String path, HttpMethod method) {
        if ("/api/runs".equals(path)) {
            return HttpMethod.POST.equals(method);
        }
        if (path.startsWith("/api/runs/")) {
            return false;
        }
        if (isPlatformAuthPath(path)) {
            return false;
        }
        if (path.startsWith("/api/workspaces/") && path.endsWith("/sessions")) {
            return true;
        }
        return LEGACY_RUNTIME_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix));
    }

    private boolean isPlatformAuthPath(String path) {
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/logout")
                || path.equals("/api/auth/me")
                || path.equals("/api/auth/refresh");
    }

    private BackendJavaProcess backendFor(String linuxServerId) {
        return liveBackendsByServer().get(linuxServerId);
    }

    private Map<String, BackendJavaProcess> liveBackendsByServer() {
        Map<String, BackendJavaProcess> result = new LinkedHashMap<>();
        for (BackendRuntimeSnapshot snapshot : heartbeatStore.liveBackendSnapshots()) {
            BackendJavaProcess backend = snapshot.backendProcess();
            if (backend != null) {
                result.merge(backend.linuxServerId().value(), backend, this::latestBackend);
            }
        }
        return result;
    }

    private BackendJavaProcess latestBackend(BackendJavaProcess left, BackendJavaProcess right) {
        return right.lastHeartbeatAt().isAfter(left.lastHeartbeatAt()) ? right : left;
    }

    private Mono<byte[]> requestBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(buffer -> {
                    byte[] result = bytes(buffer);
                    DataBufferUtils.release(buffer);
                    return result;
                })
                .defaultIfEmpty(new byte[0]);
    }

    private HttpResponse<byte[]> send(
            ServerWebExchange exchange,
            BackendJavaProcess backend,
            byte[] body) throws Exception {
        URI uri = exchange.getRequest().getURI();
        String pathAndQuery = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(backend.listenUrl()) + pathAndQuery))
                .timeout(FORWARD_TIMEOUT);
        for (String headerName : FORWARDED_HEADERS) {
            copyHeader(exchange, builder, headerName);
        }
        builder.header(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER, "true");
        if (exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER) == null) {
            builder.header(TraceConstants.TRACE_ID_HEADER, traceId(exchange));
        }
        HttpRequest.BodyPublisher publisher = body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        return httpClient.send(
                builder.method(exchange.getRequest().getMethod().name(), publisher).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private void copyHeader(ServerWebExchange exchange, HttpRequest.Builder builder, String headerName) {
        List<String> values = exchange.getRequest().getHeaders().get(headerName);
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                builder.header(headerName, value);
            }
        }
    }

    private Mono<Void> writeForwardResponse(ServerWebExchange exchange, HttpResponse<byte[]> response) {
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(response.statusCode()));
        response.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                .ifPresent(value -> exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, value));
        response.headers().firstValue(TraceConstants.TRACE_ID_HEADER)
                .ifPresent(value -> exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, value));
        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        return exchange.getResponse().writeWith(Mono.just(bufferFactory.wrap(response.body())));
    }

    private Mono<Void> writeError(
            ServerWebExchange exchange,
            ErrorCode errorCode,
            String message,
            Map<String, Object> details) {
        String traceId = traceId(exchange);
        try {
            byte[] body = objectMapper.writeValueAsBytes(ApiErrorResponse.of(errorCode, message, traceId, details));
            exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(errorCode.httpStatus()));
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }

    private byte[] bytes(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return bytes;
    }

    private String traceId(ServerWebExchange exchange) {
        String traceId = exchange.getResponse().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        }
        return traceId == null || traceId.isBlank() ? "trace_user_opencode_route" : traceId;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

}
