package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalWorkspaceApplicationService;
import com.enterprise.testagent.opencode.runtime.localclient.LocalWorkspaceApplicationService.LocalWorkspaceDeleted;
import com.enterprise.testagent.opencode.runtime.localclient.LocalWorkspaceApplicationService.LocalWorkspaceView;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 本地工作区注册与注销 HTTP 入口；任意 Java 接入后精确转发到连接持有节点。 */
@RestController
public class LocalWorkspaceController {

    private static final String BASE = "/api/internal/platform/workspace-management/local-workspaces";

    private final LocalWorkspaceApplicationService service;
    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;

    public LocalWorkspaceController(
            LocalWorkspaceApplicationService service,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder) {
        this.service = Objects.requireNonNull(service);
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.forwarder = Objects.requireNonNull(forwarder);
    }

    @PostMapping(BASE)
    public Mono<ApiResponse<LocalWorkspaceView>> create(
            @RequestBody CreateLocalWorkspaceRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        if (request == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "本地工作区请求不能为空");
        }
        // 本地根目录验证通过反向隧道同步等待客户端回包，必须离开 WebFlux event-loop。
        return Mono.fromCallable(() -> {
                    LocalClientInstanceId clientInstanceId = new LocalClientInstanceId(request.clientInstanceId());
                    LocalClientConnectionRoute route = service.requireOwnedOnlineRoute(userId, clientInstanceId);
                    BackendJavaProcess backend = routeResolver.requireBackend(route.backendProcessId());
                    if (!routeResolver.isCurrent(backend.backendProcessId())) {
                        requireNotAlreadyRouted(exchange);
                        return forwarder.forwardTyped(
                                exchange,
                                backend,
                                request,
                                new TypeReference<ApiResponse<LocalWorkspaceView>>() { });
                    }
                    return ApiResponse.ok(service.create(
                            userId, clientInstanceId, request.name(), request.rootPath(), traceId), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping(BASE + "/{workspaceId}")
    public Mono<ApiResponse<LocalWorkspaceDeleted>> delete(
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return Mono.fromCallable(() -> {
                    WorkspaceId requestedWorkspace = new WorkspaceId(workspaceId);
                    LocalClientConnectionRoute route = service.findOwnedWorkspaceOnlineRoute(userId, requestedWorkspace)
                            .orElse(null);
                    if (route != null) {
                        BackendJavaProcess backend = routeResolver.requireBackend(route.backendProcessId());
                        if (!routeResolver.isCurrent(backend.backendProcessId())) {
                            requireNotAlreadyRouted(exchange);
                            return forwarder.forwardTyped(
                                    exchange,
                                    backend,
                                    null,
                                    new TypeReference<ApiResponse<LocalWorkspaceDeleted>>() { });
                        }
                    }
                    return ApiResponse.ok(service.archive(userId, requestedWorkspace, traceId), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 持久化最近选择，使客户端重新打开网页后仍能恢复上一次本地工作区。 */
    @PostMapping(BASE + "/{workspaceId}/recent")
    public Mono<ApiResponse<LocalWorkspaceView>> markRecent(
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        return Mono.fromCallable(() -> ApiResponse.ok(
                        service.markRecent(userId, new WorkspaceId(workspaceId)), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void requireNotAlreadyRouted(ServerWebExchange exchange) {
        if ("true".equalsIgnoreCase(exchange.getRequest().getHeaders()
                .getFirst(BackendHttpForwarder.ROUTED_HEADER))) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端连接在转发期间已迁移");
        }
    }

    public record CreateLocalWorkspaceRequest(String clientInstanceId, String name, String rootPath) {
    }
}
