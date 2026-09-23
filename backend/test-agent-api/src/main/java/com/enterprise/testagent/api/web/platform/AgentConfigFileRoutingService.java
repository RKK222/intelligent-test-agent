package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalRuntimeCapabilityGuard;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
import com.enterprise.testagent.workspace.AgentConfigApplicationService;
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
    private final LocalRuntimeCapabilityGuard localRuntimeCapabilityGuard;
    private LocalClientConnectionStore localConnectionStore;
    private LocalClientInstanceRepository localInstanceRepository;

    AgentConfigFileRoutingService(
            AgentConfigApplicationService service,
            BackendJavaRouteResolver routeResolver,
            LocalRuntimeCapabilityGuard localRuntimeCapabilityGuard) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.localRuntimeCapabilityGuard = Objects.requireNonNull(
                localRuntimeCapabilityGuard, "localRuntimeCapabilityGuard must not be null");
    }

    /**
     * 根据 Agent 配置 scope/worktree/workspace 归属返回浏览器应连接的目标文件 WebSocket 后端。
     */
    AgentConfigDtos.FileRouteResponse route(AgentConfigDtos.FileRouteRequest request) {
        return route(request, null);
    }

    /**
     * 带登录用户坐标的路由入口；本地个人公共能力必须在返回目标 Java 前完成 owner fencing。
     */
    AgentConfigDtos.FileRouteResponse route(AgentConfigDtos.FileRouteRequest request, UserId userId) {
        AgentConfigDtos.FileRouteRequest resolved = request == null
                ? new AgentConfigDtos.FileRouteRequest(null, null, null, null, null, null)
                : request;
        String scope = normalizeScope(resolved.scope());
        String linuxServerId = SCOPE_PUBLIC.equals(scope)
                ? publicLinuxServerId(resolved, userId)
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

    private String publicLinuxServerId(AgentConfigDtos.FileRouteRequest request, UserId userId) {
        if (isLocalPersonalReference(request.worktreeId())) {
            return localClientLinuxServerId(localReference(request.worktreeId()), userId);
        }
        if (normalizeOptional(request.localClientInstanceId()) != null) {
            return localClientLinuxServerId(request, userId);
        }
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

    /** 本地个人公共能力只允许绑定当前用户拥有、当前 generation 的客户端连接。 */
    private String localClientLinuxServerId(AgentConfigDtos.FileRouteRequest request, UserId userId) {
        if (localConnectionStore == null || localInstanceRepository == null) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "本地客户端路由服务未装配");
        }
        LocalClientInstanceId clientId = new LocalClientInstanceId(request.localClientInstanceId().trim());
        LocalClientConnectionRoute route = localConnectionStore.find(clientId)
                .orElseThrow(() -> new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "本地客户端离线"));
        if (userId != null && !userId.equals(route.userId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端实例不属于当前用户");
        }
        if (request.connectionGeneration() == null
                || request.connectionGeneration() != route.connectionGeneration()) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端连接已换代，请重新路由");
        }
        var instance = localInstanceRepository.findById(clientId)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "本地客户端实例不存在"));
        if (!instance.selfUpdateCapabilities().contains("PUBLIC_CAPABILITY_PERSONAL_EDIT_V1")) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "当前本地客户端不支持公共能力个人编辑");
        }
        return routeResolver.requireBackend(route.backendProcessId()).linuxServerId().value();
    }

    private boolean isLocalPersonalReference(String worktreeId) {
        return worktreeId != null && worktreeId.startsWith("LOCAL_CLIENT_PERSONAL:");
    }

    private AgentConfigDtos.FileRouteRequest localReference(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || parts[1].isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "本地客户端个人配置路由无效");
        }
        long generation;
        try {
            generation = Long.parseLong(parts[2]);
        } catch (NumberFormatException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "本地客户端连接代次无效");
        }
        return new AgentConfigDtos.FileRouteRequest("PUBLIC", null, null, null, parts[1], generation);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void configureLocalClientServices(
            LocalClientConnectionStore localConnectionStore,
            LocalClientInstanceRepository localInstanceRepository) {
        this.localConnectionStore = localConnectionStore;
        this.localInstanceRepository = localInstanceRepository;
    }

    private String workspaceLinuxServerId(AgentConfigDtos.FileRouteRequest request) {
        String workspaceId = requireText(request.workspaceId(), "workspaceId 不能为空", "workspaceId");
        localRuntimeCapabilityGuard.requireWorkspaceSupported(
                new WorkspaceId(workspaceId),
                "agentConfig",
                "本地 OpenCode 工作区首版不开放 Agent 配置管理");
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
