package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.api.ApiErrorResponse;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.workspace.AgentConfigApplicationService;
import com.icbc.testagent.workspace.AgentConfigResponses;
import com.icbc.testagent.workspace.WorkspaceServerIdentity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

/**
 * Agent 配置跨后端路由服务；前端始终访问当前后端，由这里按 linuxServerId 代理到目标后端。
 */
@Service
class AgentConfigBackendRoutingService {

    private static final Duration FORWARD_TIMEOUT = Duration.ofSeconds(30);
    private static final String LOCAL_REPOSITORY_PATH =
            "/api/internal/platform/workspace-management/agent-config/public/repositories/local";

    private final AgentConfigApplicationService service;
    private final WorkspaceServerIdentity serverIdentity;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
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
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    /**
     * 单元测试便捷构造器：只支持本机执行，不访问 Redis 或远端 HTTP。
     */
    AgentConfigBackendRoutingService(AgentConfigApplicationService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.serverIdentity = new WorkspaceServerIdentity("linux-1");
        this.heartbeatStore = new OpencodeProcessHeartbeatStore() {
            @Override public void recordBackendHeartbeat(com.icbc.testagent.domain.opencodeprocess.BackendProcessId backendProcessId, java.time.Instant heartbeatAt) {}
            @Override public void recordBackendSnapshot(BackendRuntimeSnapshot snapshot) {}
            @Override public void recordManagerSnapshot(com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot snapshot) {}
            @Override public void recordOpencodeHeartbeat(com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId processId, java.time.Instant heartbeatAt) {}
            @Override public List<BackendRuntimeSnapshot> liveBackendSnapshots() { return List.of(); }
            @Override public List<com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot> liveManagerSnapshots() { return List.of(); }
            @Override public java.util.Set<com.icbc.testagent.domain.opencodeprocess.BackendProcessId> liveBackendProcessIds() { return java.util.Set.of(); }
            @Override public java.util.Set<com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId> liveOpencodeProcessIds() { return java.util.Set.of(); }
            @Override public void cleanupExpiredHeartbeats() {}
        };
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.httpClient = HttpClient.newHttpClient();
    }

    List<AgentConfigResponses.PublicRepositoryStatusResponse> listPublicRepositories(ServerWebExchange exchange, String traceId) {
        Map<String, BackendJavaProcess> remoteBackends = remoteBackendsByServer();
        List<AgentConfigResponses.PublicRepositoryStatusResponse> responses = new ArrayList<>();
        responses.add(service.localPublicRepositoryStatus());
        for (BackendJavaProcess backend : remoteBackends.values().stream()
                .sorted(Comparator.comparing(process -> process.linuxServerId().value()))
                .toList()) {
            try {
                ApiResponse<AgentConfigResponses.PublicRepositoryStatusResponse> response = forward(
                        exchange,
                        backend.linuxServerId().value(),
                        LOCAL_REPOSITORY_PATH,
                        "GET",
                        null,
                        new TypeReference<>() {});
                responses.add(response.data());
            } catch (PlatformException exception) {
                responses.add(new AgentConfigResponses.PublicRepositoryStatusResponse(
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
        return responses;
    }

    Optional<String> forwardTargetForPublicWorktree(String worktreeId) {
        return service.publicWorktreeLinuxServerId(worktreeId)
                .filter(linuxServerId -> !serverIdentity.linuxServerId().equals(linuxServerId));
    }

    Optional<String> forwardTargetForRequestedServer(String linuxServerId) {
        if (linuxServerId == null || linuxServerId.isBlank() || serverIdentity.linuxServerId().equals(linuxServerId.trim())) {
            return Optional.empty();
        }
        return Optional.of(linuxServerId.trim());
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
        BackendJavaProcess backend = backendFor(linuxServerId);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(backend.listenUrl()) + pathAndQuery))
                    .timeout(FORWARD_TIMEOUT)
                    .header("X-Trace-Id", traceId(exchange))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json");
            String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !authorization.isBlank()) {
                builder.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            HttpRequest.BodyPublisher body = requestBody == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody));
            HttpResponse<String> response = httpClient.send(builder.method(method, body).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), responseType);
            }
            ApiErrorResponse error = objectMapper.readValue(response.body(), ApiErrorResponse.class);
            throw new PlatformException(errorCode(error.code()), error.message(), error.details());
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "目标服务器后端不可用",
                    Map.of("linuxServerId", linuxServerId),
                    exception);
        }
    }

    private BackendJavaProcess backendFor(String linuxServerId) {
        BackendJavaProcess backend = remoteBackendsByServer().get(linuxServerId);
        if (backend == null) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "目标服务器后端不可用", Map.of("linuxServerId", linuxServerId));
        }
        return backend;
    }

    private Map<String, BackendJavaProcess> remoteBackendsByServer() {
        Map<String, BackendJavaProcess> result = new LinkedHashMap<>();
        for (BackendRuntimeSnapshot snapshot : heartbeatStore.liveBackendSnapshots()) {
            BackendJavaProcess backend = snapshot.backendProcess();
            if (!serverIdentity.linuxServerId().equals(backend.linuxServerId().value())) {
                result.putIfAbsent(backend.linuxServerId().value(), backend);
            }
        }
        return result;
    }

    private ErrorCode errorCode(String code) {
        try {
            return ErrorCode.valueOf(code);
        } catch (Exception exception) {
            return ErrorCode.INTERNAL_ERROR;
        }
    }

    private String traceId(ServerWebExchange exchange) {
        String traceId = exchange.getResponse().getHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        }
        return traceId == null || traceId.isBlank() ? "trace_agent_config_forward" : traceId;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
