package com.icbc.testagent.api.web.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.api.ApiErrorResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.observability.TraceIdSupport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 统一拦截已经作废的旧 HTTP 入口，避免旧 URL 继续进入鉴权、跨后端路由或 Controller。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
public class LegacyApiGoneWebFilter implements WebFilter {

    private static final Set<String> STABLE_AUTH_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/me",
            "/api/auth/refresh");
    private static final Set<String> LEGACY_EXACT_PATHS = Set.of(
            "/api/agents",
            "/api/models",
            "/api/providers",
            "/api/commands",
            "/api/references",
            "/api/status",
            "/api/config",
            "/api/worktrees",
            "/api/runs",
            "/api/sessions",
            "/api/workspaces");
    private static final List<String> LEGACY_PREFIXES = List.of(
            "/api/fs/",
            "/api/vcs/",
            "/api/lsp/",
            "/api/mcp/",
            "/api/global/",
            "/api/provider/",
            "/api/worktrees/",
            "/api/runs/",
            "/api/sessions/",
            "/api/workspaces/");
    private static final String WORKSPACE_MANAGEMENT_WORKSPACE_PREFIX =
            "/api/internal/platform/workspace-management/workspaces/";
    private static final String AGENT_CONFIG_PUBLIC_FILES_PREFIX =
            "/api/internal/platform/workspace-management/agent-config/public/files";
    private static final String AGENT_CONFIG_WORKSPACE_PREFIX =
            "/api/internal/platform/workspace-management/agent-config/workspaces/";
    private static final String MANAGER_BACKENDS_PATH =
            "/api/internal/platform/opencode-runtime/manager-backends";
    private static final String BACKEND_PROCESS_METRICS_PREFIX =
            "/api/internal/platform/opencode-runtime/management/backend-processes/";

    private final ObjectMapper objectMapper;

    /**
     * 使用 Spring 共享 ObjectMapper 写出统一 JSON 错误响应。
     */
    @Autowired
    public LegacyApiGoneWebFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 对旧入口返回 410；新 internal platform、agent-scoped 和认证入口继续放行。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!isGone(path)) {
            return chain.filter(exchange);
        }
        return gone(exchange, path);
    }

    private boolean isGone(String path) {
        if (!path.startsWith("/api/") || STABLE_AUTH_PATHS.contains(path)) {
            return false;
        }
        if (LEGACY_EXACT_PATHS.contains(path) || LEGACY_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        return isInternalCompatibilityPath(path);
    }

    private boolean isInternalCompatibilityPath(String path) {
        if (MANAGER_BACKENDS_PATH.equals(path)) {
            return true;
        }
        if (path.startsWith(BACKEND_PROCESS_METRICS_PREFIX) && path.endsWith("/metrics")) {
            return true;
        }
        if (path.startsWith(WORKSPACE_MANAGEMENT_WORKSPACE_PREFIX) && containsWorkspaceHttpFileSegment(path)) {
            return true;
        }
        if (path.equals(AGENT_CONFIG_PUBLIC_FILES_PREFIX)
                || path.equals(AGENT_CONFIG_PUBLIC_FILES_PREFIX + "/content")) {
            return true;
        }
        return path.startsWith(AGENT_CONFIG_WORKSPACE_PREFIX) && containsWorkspaceHttpFileSegment(path);
    }

    private boolean containsWorkspaceHttpFileSegment(String path) {
        return path.contains("/files")
                || path.contains("/files/")
                || path.contains("/files/status")
                || path.contains("/files/content");
    }

    private Mono<Void> gone(ServerWebExchange exchange, String path) {
        String traceId = traceId(exchange);
        exchange.getResponse().setStatusCode(HttpStatus.GONE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);
        try {
            ApiErrorResponse body = ApiErrorResponse.of(
                    ErrorCode.API_GONE,
                    "接口已作废，请改用新的 internal platform 或 agent-scoped API",
                    traceId,
                    Map.of("path", path));
            byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception exception) {
            return exchange.getResponse().setComplete();
        }
    }

    private String traceId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(TraceConstants.TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String traceId && TraceIdSupport.isValid(traceId)) {
            return traceId;
        }
        return TraceIdSupport.resolve(exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
    }
}
