package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.opencode.runtime.process.WorkspaceBackendServerResponse;
import com.icbc.testagent.opencode.runtime.process.WorkspaceFileRouteResponse;
import com.icbc.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
import java.util.List;
import java.util.function.Function;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 工作空间文件 WebSocket HTTP 入口，负责路由发现、后端服务器列表和短期 ticket 签发。
 */
@RestController
public class WorkspaceFileSocketController {

    private final WorkspaceFileRoutingService routingService;
    private final WorkspaceFileSocketTicketService ticketService;

    /**
     * 注入路由与 ticket 服务，Controller 不直接访问文件系统或 Repository。
     */
    public WorkspaceFileSocketController(
            WorkspaceFileRoutingService routingService,
            WorkspaceFileSocketTicketService ticketService) {
        this.routingService = routingService;
        this.ticketService = ticketService;
    }

    /**
     * 为普通工作空间文件操作定位目标后端服务器。
     */
    @PostMapping({
            "/api/workspaces/{workspaceId}/file-ws-route",
            "/api/internal/platform/workspace-management/workspaces/{workspaceId}/file-ws-route"
    })
    public Mono<ApiResponse<WorkspaceFileRouteResponse>> routeWorkspace(
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return blocking(exchange, traceId -> routingService.routeWorkspace(
                principal.userId(),
                "opencode",
                new WorkspaceId(workspaceId),
                traceId));
    }

    /**
     * 超级管理员列出可直连的后端服务器，用于跨服务器工作空间选择器。
     */
    @GetMapping("/api/internal/platform/workspace-management/backend-servers")
    public Mono<ApiResponse<List<WorkspaceBackendServerResponse>>> listBackendServers(ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return blocking(exchange, traceId -> routingService.listBackendServers(principal.userId(), "opencode", traceId));
    }

    /**
     * 在目标后端签发文件 WebSocket 一次性 ticket。
     */
    @PostMapping("/api/internal/platform/workspace-management/file-ws/tickets")
    public Mono<ApiResponse<WorkspaceFileSocketDtos.TicketResponse>> createTicket(
            @RequestBody(required = false) WorkspaceFileSocketDtos.TicketRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        WorkspaceFileSocketDtos.TicketRequest resolved = request == null
                ? new WorkspaceFileSocketDtos.TicketRequest(null, null, null)
                : request;
        return blocking(exchange, traceId -> ticketService.createTicket(principal, resolved, traceId));
    }

    private <T> Mono<ApiResponse<T>> blocking(ServerWebExchange exchange, Function<String, T> action) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return Mono.fromCallable(() -> ApiResponse.ok(action.apply(traceId), traceId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
