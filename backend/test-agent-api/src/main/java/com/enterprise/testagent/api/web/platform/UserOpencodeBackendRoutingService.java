package com.enterprise.testagent.api.web.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.api.ApiErrorResponse;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationRunContext;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRuntimeTargetRepository;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import com.enterprise.testagent.workspace.WorkspaceServerIdentity;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 用户 opencode 进程跨后端路由服务。
 *
 * <p>当前 Java 只控制本服务器 manager；用户已有 ACTIVE binding 时始终路由到 binding 所属服务器。
 * 尚未绑定时，仅首次进程状态和初始化请求按集群实时负载选服，再由目标 Java 操作本机 manager。
 */
@Service
class UserOpencodeBackendRoutingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserOpencodeBackendRoutingService.class);
    private static final String OPENCODE_AGENT_ID = "opencode";
    private static final String AGENT_PREFIX = "/api/internal/agent/";
    private static final String PROCESS_STATUS_PATH = "/api/internal/agent/opencode/processes/me";
    private static final String PROCESS_INITIALIZE_PATH = PROCESS_STATUS_PATH + "/initialize";
    private static final String PLATFORM_RUNTIME_PREFIX = "/api/internal/platform/opencode-runtime";
    private static final String INTERNAL_MODEL_OBSERVABILITY_PREFIX =
            PLATFORM_RUNTIME_PREFIX + "/internal-model-observability";
    private static final String CONFIGURATION_WORKSPACE_PREFIX =
            "/api/internal/platform/configuration-management/applications/";
    private static final String WORKSPACE_MANAGEMENT_PREFIX =
            "/api/internal/platform/workspace-management/";
    private static final int DEFAULT_MAX_ROUTED_REQUEST_BODY_BYTES = 32 * 1024 * 1024;

    private final UserOpencodeProcessAssignmentService assignmentService;
    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final ObjectMapper objectMapper;
    private final ConversationContextStore conversationContextStore;
    private final int maxRoutedRequestBodyBytes;
    private SessionCollaborationShareService sessionShareService;
    private LocalClientWorkspaceRepository localClientWorkspaceRepository;
    private LocalClientConnectionStore localClientConnectionStore;
    private SessionRuntimeTargetRepository sessionRuntimeTargetRepository;

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
        this.maxRoutedRequestBodyBytes = DEFAULT_MAX_ROUTED_REQUEST_BODY_BYTES;
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
        this.maxRoutedRequestBodyBytes = DEFAULT_MAX_ROUTED_REQUEST_BODY_BYTES;
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
        this.maxRoutedRequestBodyBytes = DEFAULT_MAX_ROUTED_REQUEST_BODY_BYTES;
    }

    /** 仅供路由层边界测试注入较小请求体上限，生产构造固定使用 32 MiB。 */
    UserOpencodeBackendRoutingService(
            UserOpencodeProcessAssignmentService assignmentService,
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            ConversationContextStore conversationContextStore,
            int maxRoutedRequestBodyBytes) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.routeResolver = testRouteResolver(serverIdentity, heartbeatStore);
        this.forwarder = new BackendHttpForwarder(objectMapper, httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.conversationContextStore = Objects.requireNonNull(
                conversationContextStore,
                "conversationContextStore must not be null");
        if (maxRoutedRequestBodyBytes <= 0) {
            throw new IllegalArgumentException("maxRoutedRequestBodyBytes must be greater than zero");
        }
        this.maxRoutedRequestBodyBytes = maxRoutedRequestBodyBytes;
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
     * 解析当前请求是否需要路由到远端后端；已有 binding 优先，未绑定的首次请求才按全局负载选服。
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
        UserId routingUserId = routingUserId(exchange, principal);
        Optional<String> boundTarget = assignmentService.routingLinuxServerId(routingUserId, agentId.get());
        if (boundTarget.isPresent()) {
            return boundTarget.flatMap(routeResolver::remoteTarget);
        }
        if (!isInitialAllocationRequest(exchange)) {
            return Optional.empty();
        }
        return routeResolver.selectLeastLoadedInitializableServer()
                .flatMap(routeResolver::remoteTarget)
                .map(LinuxServerId::value);
    }

    /**
     * 解析路由并在需要读取或转发大请求体的场景缓存请求体；携带 contextToken 时只读 Redis 快照，禁止查询进程 assignment。
     */
    Mono<RoutingResolution> resolveRoute(ServerWebExchange exchange, AuthPrincipal principal) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        if (exchange.getRequest().getHeaders().getFirst(BackendHttpForwarder.ROUTED_HEADER) != null) {
            return Mono.just(new RoutingResolution(exchange, Optional.empty()));
        }
        if (routeAgentId(exchange).isEmpty()) {
            return Mono.just(new RoutingResolution(exchange, Optional.empty()));
        }
        return Mono.fromCallable(() -> routingUserId(exchange, principal))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(routingUserId -> resolveRoute(exchange, routingUserId));
    }

    /** 分享上下文解析可能访问数据库，必须在上层 bounded-elastic 调度后再进入具体路由分支。 */
    private Mono<RoutingResolution> resolveRoute(
            ServerWebExchange exchange,
            UserId routingUserId) {
        if (requiresLocalBodyInspection(exchange)) {
            return cacheRequestBody(exchange)
                    .map(cached -> resolveCachedRoute(cached, routingUserId));
        }
        Optional<BackendProcessId> localTarget = localBackendTarget(exchange, null, routingUserId);
        if (localTarget.isPresent()) {
            return Mono.just(localResolution(exchange, localTarget.get()));
        }
        if (isNightExecutionTaskCreate(exchange)) {
            return cacheRequestBody(exchange)
                    .map(cached -> new RoutingResolution(
                            cached.exchange(),
                            legacyTarget(routingUserId, OPENCODE_AGENT_ID)));
        }
        Optional<String> startRunAgentId = startRunAgentId(exchange);
        if (startRunAgentId.isEmpty()) {
            // binding 查询与 Redis 全局选服均为同步 I/O，延迟到订阅后才能进入统一异常链路。
            return Mono.fromCallable(() -> new RoutingResolution(
                            exchange,
                            targetLinuxServerId(exchange, routingUserId)))
                    .subscribeOn(Schedulers.boundedElastic());
        }
        return cacheRequestBody(exchange)
                .map(cached -> resolveStartRun(cached, routingUserId, startRunAgentId.get()));
    }

    private RoutingResolution resolveCachedRoute(CachedRequest cached, UserId routingUserId) {
        Optional<BackendProcessId> localTarget = localBackendTarget(
                cached.exchange(), cached.body(), routingUserId);
        if (localTarget.isPresent()) {
            return localResolution(cached.exchange(), localTarget.get());
        }
        if (isNightExecutionTaskCreate(cached.exchange())) {
            return new RoutingResolution(
                    cached.exchange(), legacyTarget(routingUserId, OPENCODE_AGENT_ID));
        }
        Optional<String> startRunAgentId = startRunAgentId(cached.exchange());
        return startRunAgentId
                .map(agentId -> resolveStartRun(cached, routingUserId, agentId))
                .orElseGet(() -> new RoutingResolution(
                        cached.exchange(), targetLinuxServerId(cached.exchange(), routingUserId)));
    }

    private boolean requiresLocalBodyInspection(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (!HttpMethod.POST.equals(exchange.getRequest().getMethod()) || path == null) {
            return false;
        }
        return (PLATFORM_RUNTIME_PREFIX + "/sessions").equals(path)
                || isNightExecutionTaskCreate(exchange)
                || startRunAgentId(exchange).isPresent();
    }

    /** 命中本地 session/workspace 后离线直接失败，绝不回退服务器 OpenCode。 */
    private Optional<BackendProcessId> localBackendTarget(
            ServerWebExchange exchange,
            byte[] requestBody,
            UserId routingUserId) {
        if (localClientConnectionStore == null) {
            return Optional.empty();
        }
        String path = exchange.getRequest().getURI().getRawPath();
        String sessionId = segmentValue(path, "/sessions/");
        String workspaceId = segmentValue(path, "/workspaces/");
        if (sessionId == null) {
            sessionId = exchange.getRequest().getQueryParams().getFirst("sessionId");
        }
        if (workspaceId == null) {
            workspaceId = exchange.getRequest().getQueryParams().getFirst("workspaceId");
        }
        if (requestBody != null && requestBody.length > 0) {
            try {
                JsonNode body = objectMapper.readTree(requestBody);
                if (sessionId == null) sessionId = textField(body, "sessionId");
                if (workspaceId == null) workspaceId = textField(body, "workspaceId");
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        LocalClientInstanceId targetClient = null;
        if (sessionId != null && sessionRuntimeTargetRepository != null) {
            try {
                var target = sessionRuntimeTargetRepository.findBySessionId(new SessionId(sessionId)).orElse(null);
                if (target != null && target.runtimeKind() == RuntimeKind.LOCAL_CLIENT) {
                    targetClient = target.localClientInstanceId();
                }
            } catch (IllegalArgumentException exception) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Session ID 无效");
            } catch (RuntimeException exception) {
                throw new PlatformException(
                        ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                        "本地会话运行目标暂不可用");
            }
        }
        if (workspaceId != null && localClientWorkspaceRepository != null) {
            try {
                LocalClientWorkspaceBinding binding = localClientWorkspaceRepository
                        .findByWorkspaceId(new WorkspaceId(workspaceId)).orElse(null);
                if (binding != null) {
                    if (!binding.userId().equals(routingUserId)) {
                        throw new PlatformException(ErrorCode.FORBIDDEN, "本地工作区不属于当前用户");
                    }
                    if (targetClient != null && !targetClient.equals(binding.clientInstanceId())) {
                        throw new PlatformException(ErrorCode.CONFLICT, "本地会话与工作区目标不一致");
                    }
                    targetClient = binding.clientInstanceId();
                }
            } catch (PlatformException exception) {
                throw exception;
            } catch (IllegalArgumentException exception) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Workspace ID 无效");
            } catch (RuntimeException exception) {
                throw new PlatformException(
                        ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                        "本地工作区运行目标暂不可用");
            }
        }
        if (targetClient == null) {
            return Optional.empty();
        }
        LocalClientInstanceId resolvedClient = targetClient;
        LocalClientConnectionRoute route = localClientConnectionStore.find(resolvedClient)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.LOCAL_CLIENT_DISCONNECTED,
                        "本地客户端离线",
                        Map.of("clientInstanceId", resolvedClient.value())));
        if (!route.userId().equals(routingUserId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端不属于当前用户");
        }
        return Optional.of(route.backendProcessId());
    }

    private RoutingResolution localResolution(ServerWebExchange exchange, BackendProcessId backendProcessId) {
        return new RoutingResolution(
                exchange,
                Optional.empty(),
                routeResolver.isCurrent(backendProcessId)
                        ? Optional.empty()
                        : Optional.of(backendProcessId));
    }

    private static String segmentValue(String path, String marker) {
        if (path == null) return null;
        int start = path.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = path.indexOf('/', start);
        String value = end < 0 ? path.substring(start) : path.substring(start, end);
        return value.isBlank() ? null : value;
    }

    private RoutingResolution resolveStartRun(
            CachedRequest cached,
            UserId routingUserId,
            String agentId) {
        JsonNode body;
        try {
            body = cached.body().length == 0
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(cached.body());
        } catch (Exception ignored) {
            // 非法 JSON 仍交给 Controller 返回统一校验错误；兼容路径按原 assignment 路由。
            return new RoutingResolution(cached.exchange(), legacyTarget(routingUserId, agentId));
        }
        boolean contextTokenPresent = body.isObject() && body.has("contextToken");
        if (!contextTokenPresent || conversationContextStore == null) {
            return new RoutingResolution(cached.exchange(), legacyTarget(routingUserId, agentId));
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
                        routingUserId,
                        agentId,
                        sessionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.CONVERSATION_CONTEXT_EXPIRED));
        return new RoutingResolution(cached.exchange(), routeResolver.remoteTarget(context.linuxServerId()));
    }

    private Optional<String> legacyTarget(UserId routingUserId, String agentId) {
        return assignmentService.routingLinuxServerId(routingUserId, agentId)
                .flatMap(routeResolver::remoteTarget);
    }

    private Optional<String> targetLinuxServerId(ServerWebExchange exchange, UserId routingUserId) {
        if (exchange.getRequest().getHeaders().getFirst(BackendHttpForwarder.ROUTED_HEADER) != null) {
            return Optional.empty();
        }
        Optional<String> agentId = routeAgentId(exchange);
        if (agentId.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> boundTarget = assignmentService.routingLinuxServerId(routingUserId, agentId.get());
        if (boundTarget.isPresent()) {
            return boundTarget.flatMap(routeResolver::remoteTarget);
        }
        if (!isInitialAllocationRequest(exchange)) {
            return Optional.empty();
        }
        return routeResolver.selectLeastLoadedInitializableServer()
                .flatMap(routeResolver::remoteTarget)
                .map(LinuxServerId::value);
    }

    /** 分享请求必须按会话所属人的用户进程路由；目标 Java 仍会使用真实 actor 重新鉴权。 */
    private UserId routingUserId(ServerWebExchange exchange, AuthPrincipal principal) {
        String shareId = exchange.getRequest().getHeaders().getFirst(SessionShareController.SHARE_HEADER);
        if (shareId == null || shareId.isBlank()) {
            return principal.userId();
        }
        if (sessionShareService == null) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "会话分享服务未配置");
        }
        return sessionShareService.refreshAccess(
                        principal.userId(), new SessionShareId(shareId), traceId(exchange))
                .executionOwnerUserId();
    }

    /** 可选 setter 保持既有路由测试构造器兼容；生产 Spring 装配始终注入分享服务。 */
    @Autowired(required = false)
    void configureSessionShareService(SessionCollaborationShareService sessionShareService) {
        this.sessionShareService = Objects.requireNonNull(sessionShareService, "sessionShareService must not be null");
    }

    /** 生产环境按本地 workspace/session 冻结目标解析精确 Java；测试构造器可不装配。 */
    @Autowired(required = false)
    void configureLocalClientRouting(
            LocalClientWorkspaceRepository localClientWorkspaceRepository,
            LocalClientConnectionStore localClientConnectionStore,
            SessionRuntimeTargetRepository sessionRuntimeTargetRepository) {
        this.localClientWorkspaceRepository = Objects.requireNonNull(
                localClientWorkspaceRepository, "localClientWorkspaceRepository must not be null");
        this.localClientConnectionStore = Objects.requireNonNull(
                localClientConnectionStore, "localClientConnectionStore must not be null");
        this.sessionRuntimeTargetRepository = Objects.requireNonNull(
                sessionRuntimeTargetRepository, "sessionRuntimeTargetRepository must not be null");
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

    private boolean isNightExecutionTaskCreate(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        return HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && (PLATFORM_RUNTIME_PREFIX + "/night-execution/tasks").equals(path);
    }

    private Mono<CachedRequest> cacheRequestBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody(), maxRoutedRequestBodyBytes)
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
                                "请求体超过允许上限",
                                Map.of("maxBytes", maxRoutedRequestBodyBytes)))
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

    record RoutingResolution(
            ServerWebExchange exchange,
            Optional<String> linuxServerId,
            Optional<BackendProcessId> backendProcessId) {

        RoutingResolution(ServerWebExchange exchange, Optional<String> linuxServerId) {
            this(exchange, linuxServerId, Optional.empty());
        }
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

    /** 按 backendProcessId 精确转发本地客户端请求，禁止同服务器随机 Java 或本机降级。 */
    Mono<Void> forward(ServerWebExchange exchange, AuthPrincipal principal, BackendProcessId backendProcessId) {
        BackendJavaProcess backend;
        try {
            backend = routeResolver.requireBackend(backendProcessId);
        } catch (PlatformException exception) {
            return writeError(
                    exchange,
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "本地客户端连接持有 Java 不可用",
                    Map.of("backendProcessId", backendProcessId.value()));
        }
        return forwarder.forwardRawResponse(exchange, backend)
                .flatMap(response -> forwarder.writeRawResponse(exchange, response))
                .onErrorResume(exception -> {
                    LOGGER.warn("本地客户端请求转发失败 backendProcessId={} traceId={}",
                            backendProcessId.value(), traceId(exchange), exception);
                    return writeError(
                            exchange,
                            ErrorCode.OPENCODE_UNAVAILABLE,
                            "本地客户端连接持有 Java 不可用",
                            Map.of("backendProcessId", backendProcessId.value()));
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
        if (isProcessMessageGateRead(suffix, method)) {
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

    private boolean isProcessMessageGateRead(String suffix, HttpMethod method) {
        return HttpMethod.GET.equals(method)
                && "/processes/me/message-gate".equals(suffix);
    }

    private boolean isPlatformRuntimePath(String path, HttpMethod method) {
        if (!path.startsWith(PLATFORM_RUNTIME_PREFIX)) {
            return false;
        }
        // 可观测看板读取共享统计记录，不属于某个用户的 OpenCode 进程，不能跟随用户 binding 转发。
        if (path.equals(INTERNAL_MODEL_OBSERVABILITY_PREFIX)
                || path.startsWith(INTERNAL_MODEL_OBSERVABILITY_PREFIX + "/")) {
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
        // 分享管理、访问解析和分享状态 SSE 只依赖共享数据库/Redis；禁止交给会缓冲响应体的普通 HTTP 转发器。
        if (suffix.startsWith("/session-shares")
                || suffix.startsWith("/session-share-candidates")
                || suffix.endsWith("/collaboration-share")) {
            return false;
        }
        if ("/runs".equals(suffix)) {
            return HttpMethod.POST.equals(method);
        }
        return !suffix.startsWith("/runs/");
    }

    private boolean isManagedWorkspacePath(String path, HttpMethod method) {
        if (path.startsWith(CONFIGURATION_WORKSPACE_PREFIX) && path.endsWith("/workspaces")) {
            return true;
        }
        if (!path.startsWith(WORKSPACE_MANAGEMENT_PREFIX)) {
            return false;
        }
        String suffix = path.substring(WORKSPACE_MANAGEMENT_PREFIX.length());
        // 这些入口会解析个人 worktree/应用副本的本地文件或 Git，必须路由到用户
        // opencode 绑定所在的 Java；目标 Java 再按本机 manager/文件系统执行。
        if (suffix.startsWith("agent-config/public/")
                || suffix.startsWith("agent-config/operations/")
                || suffix.equals("agent-config/public")
                || suffix.startsWith("backend-servers")
                || suffix.startsWith("file-ws")) {
            return false;
        }
        if (suffix.startsWith("agent-config/workspaces/")) {
            return true;
        }
        // 应用源码打开、最近选择和副本可打开性依赖用户进程所在服务器，统一复用用户绑定路由。
        if (suffix.equals("recent-app-source")) {
            return HttpMethod.GET.equals(method) || HttpMethod.DELETE.equals(method);
        }
        if (suffix.startsWith("personal-workspaces/")
                || suffix.startsWith("workspaces/")
                || suffix.startsWith("workspace-versions/")) {
            return HttpMethod.GET.equals(method) || HttpMethod.POST.equals(method);
        }
        return suffix.startsWith("applications/")
                && (suffix.contains("/workspace-templates/")
                        || suffix.contains("/workspaces/")
                        || suffix.contains("/app-source-repositories"))
                && (HttpMethod.GET.equals(method) || HttpMethod.POST.equals(method));
    }

    private boolean isReadOnlyProcessStatusRequest(ServerWebExchange exchange) {
        return HttpMethod.GET.equals(exchange.getRequest().getMethod())
                && PROCESS_STATUS_PATH.equals(exchange.getRequest().getURI().getRawPath());
    }

    private boolean isInitialAllocationRequest(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getURI().getRawPath();
        return (HttpMethod.GET.equals(method) && PROCESS_STATUS_PATH.equals(path))
                || (HttpMethod.POST.equals(method) && PROCESS_INITIALIZE_PATH.equals(path));
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
