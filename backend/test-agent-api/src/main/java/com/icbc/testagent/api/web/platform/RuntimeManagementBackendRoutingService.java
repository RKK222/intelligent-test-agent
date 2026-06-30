package com.icbc.testagent.api.web.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.api.ApiErrorResponse;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.workspace.WorkspaceServerIdentity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
 * 运行管理命令跨 Java 后端路由服务。
 *
 * <p>manager 只连接本服务器 Java，因此超级管理员在任意后端发起容器进程命令时，
 * 需要先按在线 manager 快照定位容器所属服务器，再转发到该服务器的 Java 执行本地 manager 命令。
 */
@Service
class RuntimeManagementBackendRoutingService {

    private static final Duration FORWARD_TIMEOUT = Duration.ofSeconds(30);
    private static final List<String> FORWARDED_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.ACCEPT,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT_LANGUAGE,
            TraceConstants.TRACE_ID_HEADER);

    private final WorkspaceServerIdentity serverIdentity;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    RuntimeManagementBackendRoutingService(
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper) {
        this(serverIdentity, heartbeatStore, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    RuntimeManagementBackendRoutingService(
            WorkspaceServerIdentity serverIdentity,
            OpencodeProcessHeartbeatStore heartbeatStore,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    /**
     * 返回容器命令应转发到的远端服务器；本服务器或已转发请求返回空，避免重复路由。
     */
    Optional<String> forwardTargetForContainer(ServerWebExchange exchange, OpencodeContainerId containerId) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(containerId, "containerId must not be null");
        if (exchange.getRequest().getHeaders().getFirst(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER) != null) {
            return Optional.empty();
        }
        return containerLinuxServerId(containerId)
                .map(LinuxServerId::value)
                .filter(linuxServerId -> !serverIdentity.linuxServerId().equals(linuxServerId));
    }

    /**
     * 将当前运行管理命令转发到目标服务器 Java，并把目标统一响应解析回当前 Controller。
     */
    <T> ApiResponse<T> forward(
            ServerWebExchange exchange,
            String linuxServerId,
            TypeReference<ApiResponse<T>> responseType) {
        BackendJavaProcess backend = backendFor(linuxServerId);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(backend.listenUrl()) + pathAndQuery(exchange)))
                    .timeout(FORWARD_TIMEOUT);
            for (String headerName : FORWARDED_HEADERS) {
                copyHeader(exchange, builder, headerName);
            }
            builder.header(UserOpencodeBackendRoutingWebFilter.ROUTED_HEADER, "true");
            if (exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER) == null) {
                builder.header(TraceConstants.TRACE_ID_HEADER, traceId(exchange));
            }
            HttpResponse<String> response = httpClient.send(
                    builder.method(exchange.getRequest().getMethod().name(), HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
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

    private Optional<LinuxServerId> containerLinuxServerId(OpencodeContainerId containerId) {
        return heartbeatStore.liveManagerSnapshots().stream()
                .map(ManagerRuntimeSnapshot::container)
                .filter(container -> container.containerId().equals(containerId))
                .max(Comparator.comparing(OpencodeContainer::lastHeartbeatAt))
                .map(OpencodeContainer::linuxServerId);
    }

    private BackendJavaProcess backendFor(String linuxServerId) {
        BackendJavaProcess backend = liveBackendsByServer().get(linuxServerId);
        if (backend == null) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "目标服务器后端不可用",
                    Map.of("linuxServerId", linuxServerId));
        }
        return backend;
    }

    private Map<String, BackendJavaProcess> liveBackendsByServer() {
        Map<String, BackendJavaProcess> result = new LinkedHashMap<>();
        for (BackendRuntimeSnapshot snapshot : heartbeatStore.liveBackendSnapshots()) {
            BackendJavaProcess backend = snapshot.backendProcess();
            result.merge(backend.linuxServerId().value(), backend, this::latestBackend);
        }
        return result;
    }

    private BackendJavaProcess latestBackend(BackendJavaProcess left, BackendJavaProcess right) {
        return right.lastHeartbeatAt().isAfter(left.lastHeartbeatAt()) ? right : left;
    }

    private String pathAndQuery(ServerWebExchange exchange) {
        URI uri = exchange.getRequest().getURI();
        return uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
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

    private ErrorCode errorCode(String code) {
        try {
            return ErrorCode.valueOf(code);
        } catch (Exception exception) {
            return ErrorCode.INTERNAL_ERROR;
        }
    }

    private String traceId(ServerWebExchange exchange) {
        String traceId = exchange.getResponse().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = exchange.getRequest().getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        }
        return traceId == null || traceId.isBlank() ? "trace_runtime_management_forward" : traceId;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
