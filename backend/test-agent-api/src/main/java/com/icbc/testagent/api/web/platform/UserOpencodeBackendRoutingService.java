package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.api.ApiErrorResponse;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.run.ConversationContextStore;
import com.icbc.testagent.domain.run.ConversationRunContext;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.icbc.testagent.workspace.WorkspaceServerIdentity;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

/**
 * 用户 opencode 进程跨后端路由服务。
 *
 * <p>当前 Java 只控制本服务器 manager；如果用户 ACTIVE binding 属于其他服务器，
 * API 层把相关 HTTP 请求转发到 binding 所属服务器的 Java，由目标 Java 继续鉴权并操作本机 manager。
 */
@Service
class UserOpencodeBackendRoutingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserOpencodeBackendRoutingService.class);
    private static final String OPENCODE_AGENT_ID = "opencode";
    private static final String AGENT_PREFIX = "/api/internal/agent/";
    private static final String PROCESS_STATUS_PATH = "/api/internal/agent/opencode/processes/me";
    private static final String PLATFORM_RUNTIME_PREFIX = "/api/internal/platform/opencode-runtime";
    private static final String CONFIGURATION_WORKSPACE_PREFIX =
            "/api/internal/platform/configuration-management/applications/";
    private static final String WORKSPACE_MANAGEMENT_PREFIX =
            "/api/internal/platform/workspace-management/";
    private static final int DEFAULT_MAX_START_RUN_BODY_BYTES = 32 * 1024 * 1024;

    private final UserOpencodeProcessAssignmentService assignmentService;
    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final ObjectMapper objectMapper;
    private final ConversationContextStore conversationContextStore;
    private final int maxStartRunBodyBytes;

    @Autowired
    UserOpencodeBackendRoutingService(
            UserOpencodeProcessAssignmentService assignmentService,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            ObjectMapper objectMapper,
            ConversationContextStore conversationContextStore) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.conversationContextStore = Objects.requireNonNull(
                conversationContextStore,
                "conversationContextStore must not be null");
        this.maxStartRunBodyBytes = DEFAULT_MAX_START_RUN_BODY_BYTES;
    }

    UserOpencodeBackendRoutingService(
            UserOpencodeProcessAssignmentService assignmentService,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            ObjectMapper objectMapper) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.conversationContextStore = null;
        this.maxStartRunBodyBytes = DEFAULT_MAX_START_RUN_BODY_BYTES;
    }

    UserOpencodeBackendRoutingService(
            UserOpencodeProcessAssignmentService assignmentService,
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this(assignmentService, testRouteResolver(serverIdentity, heartbeatStore), new BackendHttpForwarder(objectMapper, httpClient), objectMapper);
    }

    UserOpencodeBackendRoutingService(
            UserOpencodeProcessAssignmentService assignmentService,
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            ConversationContextStore conversationContextStore) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.routeResolver = testRouteResolver(serverIdentity, heartbeatStore);
        this.forwarder = new BackendHttpForwarder(objectMapper, httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.conversationContextStore = Objects.requireNonNull(
                conversationContextStore,
                "conversationContextStore must not be null");
        this.maxStartRunBodyBytes = DEFAULT_MAX_START_RUN_BODY_BYTES;
    }

    /** 仅供路由层边界测试注入较小请求体上限，生产构造固定使用 32 MiB。 */
    UserOpencodeBackendRoutingService(
            UserOpencodeProcessAssignmentService assignmentService,
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            ConversationContextStore conversationContextStore,
            int maxStartRunBodyBytes) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.routeResolver = testRouteResolver(serverIdentity, heartbeatStore);
        this.forwarder = new BackendHttpForwarder(objectMapper, httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.conversationContextStore = Objects.requireNonNull(
                conversationContextStore,
                "conversationContextStore must not be null");
        if (maxStartRunBodyBytes <= 0) {
            throw new IllegalArgumentException("maxStartRunBodyBytes must be greater than zero");
        }
        this.maxStartRunBodyBytes = maxStartRunBodyBytes;
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

    @SuppressWarnings("unused")
    UserOpencodeBackendRoutingService(
            UserOpencodeProcessAssignmentService assignmentService,
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper) {
        this(assignmentService, serverIdentity, heartbeatStore, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    /**
     * 解析当前请求是否需要路由到远端 binding 所属后端。
     */
    Optional<String> targetLinuxServerId(ServerWebExchange exchange, AuthPrincipal principal) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        if (exchange.getRequest().getHeaders().getFirst(BackendHttpForwarder.ROUTED_HEADER) != null) {
            return Optional.empty();
        }
        Optional<String> agentId = routeAgentId(exchange);
        if (agentId.isEmpty()) {
            return Optional.empty();
        }
        return assignmentService.routingLinuxServerId(principal.userId(), agentId.get())
                .flatMap(routeResolver::remoteTarget);
    }

    /**
     * 解析路由并在 start-run 场景缓存请求体；携带 contextToken 时只读 Redis 快照，禁止查询进程 assignment。
     */
    Mono<RoutingResolution> resolveRoute(ServerWebExchange exchange, AuthPrincipal principal) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        if (exchange.getRequest().getHeaders().getFirst(BackendHttpForwarder.ROUTED_HEADER) != null) {
            return Mono.just(new RoutingResolution(exchange, Optional.empty()));
        }
        Optional<String> startRunAgentId = startRunAgentId(exchange);
        if (startRunAgentId.isEmpty()) {
            return Mono.just(new RoutingResolution(exchange, targetLinuxServerId(exchange, principal)));
        }
        return cacheRequestBody(exchange)
                .map(cached -> resolveStartRun(cached, principal, startRunAgentId.get()));
    }

    private RoutingResolution resolveStartRun(
            CachedRequest cached,
            AuthPrincipal principal,
            String agentId) {
        JsonNode body;
        try {
            body = cached.body().length == 0
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(cached.body());
        } catch (Exception ignored) {
            // 非法 JSON 仍交给 Controller 返回统一校验错误；兼容路径按原 assignment 路由。
            return new RoutingResolution(cached.exchange(), legacyTarget(principal, agentId));
        }
        boolean contextTokenPresent = body.isObject() && body.has("contextToken");
        if (!contextTokenPresent || conversationContextStore == null) {
            return new RoutingResolution(cached.exchange(), legacyTarget(principal, agentId));
        }
        String contextToken = textField(body, "contextToken");
        if (contextToken == null) {
            throw new PlatformException(
                    ErrorCode.CONVERSATION_CONTEXT_EXPIRED,
                    "会话运行上下文已过期或与当前请求不匹配");
        }
        String sessionIdValue = textField(body, "sessionId");
        if (sessionIdValue == null) {
            throw new PlatformException(
                    ErrorCode.CONVERSATION_CONTEXT_EXPIRED,
                    "会话运行上下文已过期或与当前请求不匹配");
        }
        SessionId sessionId;
        try {
            sessionId = new SessionId(sessionIdValue);
        } catch (RuntimeException exception) {
            throw new PlatformException(
                    ErrorCode.CONVERSATION_CONTEXT_EXPIRED,
                    "会话运行上下文已过期或与当前请求不匹配");
        }
        ConversationRunContext context = conversationContextStore.resolveForRouting(
                        contextToken,
                        principal.userId(),
                        agentId,
                        sessionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONVERSATION_CONTEXT_EXPIRED));
        return new RoutingResolution(cached.exchange(), routeResolver.remoteTarget(context.linuxServerId()));
    }

    private Optional<String> legacyTarget(AuthPrincipal principal, String agentId) {
        return assignmentService.routingLinuxServerId(principal.userId(), agentId)
                .flatMap(routeResolver::remoteTarget);
    }

    private Optional<String> startRunAgentId(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (!HttpMethod.POST.equals(exchange.getRequest().getMethod()) || path == null) {
            return Optional.empty();
        }
        if ((PLATFORM_RUNTIME_PREFIX + "/runs").equals(path)) {
            return Optional.of(OPENCODE_AGENT_ID);
        }
        return agentPathAgentId(path, HttpMethod.POST)
                .filter(ignored -> path.equals(AGENT_PREFIX + OPENCODE_AGENT_ID + "/runs"));
    }

    private Mono<CachedRequest> cacheRequestBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody(), maxStartRunBodyBytes)
                .map(buffer -> {
                    byte[] body = new byte[buffer.readableByteCount()];
                    buffer.read(body);
                    DataBufferUtils.release(buffer);
                    return new CachedRequest(withBody(exchange, body), body);
                })
                .onErrorMap(
                        DataBufferLimitException.class,
                        exception -> new PlatformException(
                                ErrorCode.VALIDATION_ERROR,
                                "Run 请求体超过允许上限",
                                Map.of("maxBytes", maxStartRunBodyBytes)))
                .defaultIfEmpty(new CachedRequest(withBody(exchange, new byte[0]), new byte[0]));
    }

    private ServerWebExchange withBody(ServerWebExchange exchange, byte[] body) {
        ServerHttpRequestDecorator request = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(body)));
            }
        };
        return exchange.mutate().request(request).build();
    }

    private static String textField(JsonNode body, String field) {
        if (body == null || !body.isObject()) {
            return null;
        }
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            return null;
        }
        return value.textValue().trim();
    }

    record RoutingResolution(ServerWebExchange exchange, Optional<String> linuxServerId) {
    }

    private record CachedRequest(ServerWebExchange exchange, byte[] body) {
    }

    /**
     * 转发原始 HTTP 请求，并把目标 Java 的响应原样写回当前响应。
     */
    Mono<Void> forward(ServerWebExchange exchange, AuthPrincipal principal, String linuxServerId) {
        BackendJavaProcess backend;
        try {
            backend = routeResolver.requireBackend(linuxServerId);
        } catch (PlatformException exception) {
            if (isReadOnlyProcessStatusRequest(exchange)) {
                return writeAllocationStatus(exchange, principal);
            }
            return writeError(
                    exchange,
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "目标服务器后端不可用",
                    Map.of("linuxServerId", linuxServerId));
        }
        return forwarder.forwardRawResponse(exchange, backend)
                .flatMap(response -> {
                    if (shouldFallbackToAllocationStatus(exchange, response)) {
                        return writeAllocationStatus(exchange, principal);
                    }
                    return forwarder.writeRawResponse(exchange, response);
                })
                .onErrorResume(exception -> {
                    LOGGER.warn("用户 opencode 请求转发失败 linuxServerId={} traceId={}",
                            linuxServerId, traceId(exchange), exception);
                    if (isReadOnlyProcessStatusRequest(exchange)) {
                        return writeAllocationStatus(exchange, principal);
                    }
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
        if (isPlatformRuntimePath(path, method)) {
            return Optional.of(OPENCODE_AGENT_ID);
        }
        if (isManagedWorkspacePath(path, method)) {
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
        if (isProcessWeakHealthRead(suffix, method)) {
            return Optional.empty();
        }
        if (isProcessInitializeOperationRead(suffix, method)) {
            return Optional.empty();
        }
        if (suffix.startsWith("/runs/")) {
            return Optional.empty();
        }
        return Optional.of(agentId);
    }

    private boolean isProcessInitializeOperationRead(String suffix, HttpMethod method) {
        return HttpMethod.GET.equals(method)
                && suffix.startsWith("/processes/me/initialize-operations/");
    }

    private boolean isProcessWeakHealthRead(String suffix, HttpMethod method) {
        return HttpMethod.GET.equals(method)
                && "/processes/me/health".equals(suffix);
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

    private boolean isManagedWorkspacePath(String path, HttpMethod method) {
        if (!HttpMethod.POST.equals(method)) {
            return false;
        }
        if (path.startsWith(CONFIGURATION_WORKSPACE_PREFIX) && path.endsWith("/workspaces")) {
            return true;
        }
        if (!path.startsWith(WORKSPACE_MANAGEMENT_PREFIX)) {
            return false;
        }
        String suffix = path.substring(WORKSPACE_MANAGEMENT_PREFIX.length());
        return (suffix.startsWith("applications/") && suffix.contains("/workspace-templates/") && suffix.endsWith("/versions"))
                || (suffix.startsWith("workspace-versions/") && suffix.endsWith("/git-pull"));
    }

    private boolean isReadOnlyProcessStatusRequest(ServerWebExchange exchange) {
        return HttpMethod.GET.equals(exchange.getRequest().getMethod())
                && PROCESS_STATUS_PATH.equals(exchange.getRequest().getURI().getRawPath());
    }

    private boolean shouldFallbackToAllocationStatus(
            ServerWebExchange exchange,
            HttpResponse<byte[]> response) {
        return isReadOnlyProcessStatusRequest(exchange) && response.statusCode() >= 500;
    }

    private Mono<Void> writeAllocationStatus(ServerWebExchange exchange, AuthPrincipal principal) {
        String traceId = traceId(exchange);
        try {
            var response = assignmentService.allocationStatus(
                    principal.userId(),
                    OPENCODE_AGENT_ID,
                    "已分配 opencode 专属进程，但目标服务器后端不可用，暂无法确认进程健康状态",
                    traceId);
            byte[] body = objectMapper.writeValueAsBytes(ApiResponse.ok(
                    RuntimeDtos.UserOpencodeProcessResponse.from(response),
                    traceId));
            exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(200));
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
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

    /** WebFilter 发生上下文错误时直接写统一 API 错误，避免绕过 ControllerAdvice 后退化为 500。 */
    Mono<Void> writePlatformError(ServerWebExchange exchange, PlatformException exception) {
        return writeError(
                exchange,
                exception.errorCode(),
                exception.getMessage(),
                exception.details());
    }

    private String traceId(ServerWebExchange exchange) {
        String traceId = exchange.getResponse().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        }
        return traceId == null || traceId.isBlank() ? "trace_user_opencode_route" : traceId;
    }

}
