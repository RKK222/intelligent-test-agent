package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.api.ApiErrorResponse;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.icbc.testagent.workspace.WorkspaceServerIdentity;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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

    private final UserOpencodeProcessAssignmentService assignmentService;
    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final ObjectMapper objectMapper;

    @Autowired
    UserOpencodeBackendRoutingService(
            UserOpencodeProcessAssignmentService assignmentService,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            ObjectMapper objectMapper) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    UserOpencodeBackendRoutingService(
            UserOpencodeProcessAssignmentService assignmentService,
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this(assignmentService, testRouteResolver(serverIdentity, heartbeatStore), new BackendHttpForwarder(objectMapper, httpClient), objectMapper);
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
        if (isPlatformRuntimePath(path, method) || isLegacyRuntimePath(path, method)) {
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

    private String traceId(ServerWebExchange exchange) {
        String traceId = exchange.getResponse().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        }
        return traceId == null || traceId.isBlank() ? "trace_user_opencode_route" : traceId;
    }

}
