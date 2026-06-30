package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.icbc.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
import com.icbc.testagent.workspace.AgentConfigApplicationService;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Agent 配置文件 WebSocket 路由服务，只解析目标后端，不执行文件或 Git 操作。
 */
@Service
class AgentConfigFileRoutingService {

    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SCOPE_WORKSPACE = "WORKSPACE";

    private final AgentConfigApplicationService service;
    private final BackendJavaRouteResolver routeResolver;

    AgentConfigFileRoutingService(
            AgentConfigApplicationService service,
            BackendJavaRouteResolver routeResolver) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
    }

    /**
     * 根据 Agent 配置 scope/worktree/workspace 归属返回浏览器应连接的目标文件 WebSocket 后端。
     */
    AgentConfigDtos.FileRouteResponse route(AgentConfigDtos.FileRouteRequest request) {
        AgentConfigDtos.FileRouteRequest resolved = request == null
                ? new AgentConfigDtos.FileRouteRequest(null, null, null, null)
                : request;
        String scope = normalizeScope(resolved.scope());
        String linuxServerId = SCOPE_PUBLIC.equals(scope)
                ? publicLinuxServerId(resolved)
                : workspaceLinuxServerId(resolved);
        BackendJavaProcess backend = backendFor(linuxServerId);
        return new AgentConfigDtos.FileRouteResponse(
                scope,
                normalizeOptional(resolved.workspaceId()),
                normalizeOptional(resolved.worktreeId()),
                linuxServerId,
                trimTrailingSlash(backend.listenUrl()),
                WorkspaceFileRoutingService.WEB_SOCKET_PATH,
                routeResolver.isCurrent(linuxServerId),
                null);
    }

    private String publicLinuxServerId(AgentConfigDtos.FileRouteRequest request) {
        String worktreeId = normalizeOptional(request.worktreeId());
        String requestedLinuxServerId = normalizeOptional(request.linuxServerId());
        if (worktreeId == null) {
            if (requestedLinuxServerId == null) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "公共 Agent 配置文件服务器不能为空", Map.of("linuxServerId", ""));
            }
            return requestedLinuxServerId;
        }
        String resolved = service.publicWorktreeLinuxServerId(worktreeId).orElse(routeResolver.currentLinuxServerIdValue());
        if (requestedLinuxServerId != null && !requestedLinuxServerId.equals(resolved)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "公共 Agent worktree 与选择服务器不一致",
                    Map.of("worktreeId", worktreeId, "targetLinuxServerId", resolved, "requestedLinuxServerId", requestedLinuxServerId));
        }
        return resolved;
    }

    private String workspaceLinuxServerId(AgentConfigDtos.FileRouteRequest request) {
        String workspaceId = requireText(request.workspaceId(), "workspaceId 不能为空", "workspaceId");
        String resolved = service.workspaceAgentFilesLinuxServerId(workspaceId, normalizeOptional(request.worktreeId()));
        String requestedLinuxServerId = normalizeOptional(request.linuxServerId());
        if (requestedLinuxServerId != null && !requestedLinuxServerId.equals(resolved)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "工作空间 Agent 配置与选择服务器不一致",
                    Map.of("workspaceId", workspaceId, "targetLinuxServerId", resolved, "requestedLinuxServerId", requestedLinuxServerId));
        }
        return resolved;
    }

    private BackendJavaProcess backendFor(String linuxServerId) {
        return routeResolver.requireBackend(linuxServerId);
    }

    private String normalizeScope(String scope) {
        String value = scope == null ? "" : scope.trim().toUpperCase(java.util.Locale.ROOT);
        if (SCOPE_PUBLIC.equals(value) || SCOPE_WORKSPACE.equals(value)) {
            return value;
        }
        throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Agent 配置文件 scope 无效", Map.of("scope", scope));
    }

    private String requireText(String value, String message, String field) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message, Map.of(field, ""));
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
