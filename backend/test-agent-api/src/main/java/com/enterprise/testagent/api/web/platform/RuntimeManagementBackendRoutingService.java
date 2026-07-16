package com.enterprise.testagent.api.web.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.enterprise.testagent.workspace.WorkspaceServerIdentity;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;

    @Autowired
    RuntimeManagementBackendRoutingService(
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder) {
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder must not be null");
    }

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
        this(testRouteResolver(serverIdentity, heartbeatStore), new BackendHttpForwarder(objectMapper, httpClient));
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

    /**
     * 返回容器命令应转发到的远端服务器；本服务器或已转发请求返回空，避免重复路由。
     */
    Optional<String> forwardTargetForContainer(ServerWebExchange exchange, OpencodeContainerId containerId) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(containerId, "containerId must not be null");
        if (exchange.getRequest().getHeaders().getFirst(BackendHttpForwarder.ROUTED_HEADER) != null) {
            return Optional.empty();
        }
        return routeResolver.containerLinuxServerId(containerId)
                .flatMap(routeResolver::remoteTarget)
                .map(LinuxServerId::value);
    }

    /**
     * 将当前运行管理命令转发到目标服务器 Java，并把目标统一响应解析回当前 Controller。
     */
    <T> ApiResponse<T> forward(
            ServerWebExchange exchange,
            String linuxServerId,
            TypeReference<ApiResponse<T>> responseType) {
        BackendJavaProcess backend = routeResolver.requireBackend(linuxServerId);
        return forwarder.forwardTyped(exchange, backend, null, responseType);
    }
}
