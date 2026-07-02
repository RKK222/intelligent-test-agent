package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.icbc.testagent.workspace.AgentConfigApplicationService;
import com.icbc.testagent.workspace.AgentConfigResponses;
import com.icbc.testagent.workspace.WorkspaceServerIdentity;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

/**
 * Agent 配置跨后端路由服务；前端始终访问当前后端，由这里按 linuxServerId 代理到目标后端。
 */
@Service
class AgentConfigBackendRoutingService {

    private static final String LOCAL_REPOSITORY_PATH =
            "/api/internal/platform/workspace-management/agent-config/public/repositories/local";

    private final AgentConfigApplicationService service;
    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;

    @Autowired
    AgentConfigBackendRoutingService(
            AgentConfigApplicationService service,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder must not be null");
    }

    AgentConfigBackendRoutingService(
            AgentConfigApplicationService service,
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper) {
        this(service, serverIdentity, heartbeatStore, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    AgentConfigBackendRoutingService(
            AgentConfigApplicationService service,
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this(
                service,
                testRouteResolver(serverIdentity, heartbeatStore),
                new BackendHttpForwarder(objectMapper, httpClient));
    }

    /**
     * 单元测试便捷构造器：只支持本机执行，不访问 Redis 或远端 HTTP。
     */
    AgentConfigBackendRoutingService(AgentConfigApplicationService service) {
        this(
                service,
                testRouteResolver(new WorkspaceServerIdentity("127.0.0.1"), new OpencodeProcessHeartbeatStore() {
            @Override public void recordBackendHeartbeat(com.icbc.testagent.domain.opencodeprocess.LinuxServerId linuxServerId, java.time.Instant heartbeatAt) {}
            @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) {}
            @Override public void recordManagerSnapshot(com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot snapshot) {}
            @Override public void recordOpencodeHeartbeat(com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId processId, java.time.Instant heartbeatAt) {}
            @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
            @Override public List<com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
            @Override public java.util.Set<com.icbc.testagent.domain.opencodeprocess.LinuxServerId> liveBackendServerIds() { return java.util.Set.of(); }
            @Override public java.util.Set<com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId> liveOpencodeProcessIds() { return java.util.Set.of(); }
            @Override public void cleanupExpiredHeartbeats() {}
        }),
                new BackendHttpForwarder(new ObjectMapper().findAndRegisterModules(), HttpClient.newHttpClient()));
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

    List<AgentConfigResponses.PublicRepositoryStatusResponse> listPublicRepositories(ServerWebExchange exchange, String traceId) {
        Map<String, BackendJavaProcess> remoteBackends = routeResolver.remoteBackendsByServer();
        Map<String, AgentConfigResponses.PublicRepositoryStatusResponse> responsesByServer = new LinkedHashMap<>();
        AgentConfigResponses.PublicRepositoryStatusResponse local = service.localPublicRepositoryStatus();
        responsesByServer.put(local.linuxServerId(), local);
        for (BackendJavaProcess backend : remoteBackends.values().stream()
                .sorted(Comparator.comparing(process -> process.linuxServerId().value()))
                .toList()) {
            if (responsesByServer.containsKey(backend.linuxServerId().value())) {
                continue;
            }
            try {
                ApiResponse<AgentConfigResponses.PublicRepositoryStatusResponse> response = forward(
                        exchange,
                        backend.linuxServerId().value(),
                        LOCAL_REPOSITORY_PATH,
                        "GET",
                        null,
                        new TypeReference<>() {});
                responsesByServer.put(response.data().linuxServerId(), response.data());
            } catch (PlatformException exception) {
                responsesByServer.put(backend.linuxServerId().value(), new AgentConfigResponses.PublicRepositoryStatusResponse(
                        backend.linuxServerId().value(),
                        backend.linuxServerId().value(),
                        null,
                        null,
                        null,
                        "UNAVAILABLE",
                        false,
                        false,
                        null,
                        null,
                        exception.getMessage()));
            }
        }
        return new ArrayList<>(responsesByServer.values());
    }

    Optional<String> forwardTargetForPublicWorktree(String worktreeId) {
        return service.publicWorktreeLinuxServerId(worktreeId)
                .flatMap(routeResolver::remoteTarget);
    }

    Optional<String> forwardTargetForRequestedServer(String linuxServerId) {
        return routeResolver.remoteTarget(linuxServerId);
    }

    <T> ApiResponse<T> forward(
            ServerWebExchange exchange,
            String linuxServerId,
            Object requestBody,
            TypeReference<ApiResponse<T>> responseType) {
        URI uri = exchange.getRequest().getURI();
        String pathAndQuery = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        return forward(exchange, linuxServerId, pathAndQuery, exchange.getRequest().getMethod().name(), requestBody, responseType);
    }

    private <T> ApiResponse<T> forward(
            ServerWebExchange exchange,
            String linuxServerId,
            String pathAndQuery,
            String method,
            Object requestBody,
            TypeReference<ApiResponse<T>> responseType) {
        BackendJavaProcess backend = routeResolver.requireBackend(linuxServerId);
        return forwarder.forwardTyped(exchange, backend, pathAndQuery, method, requestBody, responseType);
    }
}
