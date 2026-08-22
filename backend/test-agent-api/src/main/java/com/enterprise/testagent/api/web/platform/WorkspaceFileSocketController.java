package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceBackendServerResponse;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRouteResponse;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final SessionCollaborationShareService shareService;

    /**
     * 注入路由与 ticket 服务，Controller 不直接访问文件系统或 Repository。
     */
    public WorkspaceFileSocketController(
            WorkspaceFileRoutingService routingService,
            WorkspaceFileSocketTicketService ticketService) {
        this(routingService, ticketService, null);
    }

    @Autowired
    public WorkspaceFileSocketController(
            WorkspaceFileRoutingService routingService,
            WorkspaceFileSocketTicketService ticketService,
            SessionCollaborationShareService shareService) {
        this.routingService = routingService;
        this.ticketService = ticketService;
        this.shareService = shareService;
    }

    /**
     * 为普通工作空间文件操作定位目标后端服务器。
     */
    @PostMapping("/api/internal/platform/workspace-management/workspaces/{workspaceId}/file-ws-route")
    public ApiResponse<WorkspaceFileRouteResponse> routeWorkspace(
            @PathVariable String workspaceId,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        WorkspaceId requestedWorkspace = new WorkspaceId(workspaceId);
        DelegatedOperationContext context = shareContext(principal, shareId, traceId);
        if (context != null) context.requireWorkspace(requestedWorkspace);
        return ApiResponse.ok(routingService.routeWorkspace(
                context == null ? principal.userId() : context.executionOwnerUserId(),
                "opencode",
                requestedWorkspace,
                traceId), traceId);
    }

    /** 为当前用户的本地客户端目录选择器定位精确的反向连接持有 Java。 */
    @PostMapping("/api/internal/platform/workspace-management/local-clients/{clientInstanceId}/directory-picker/file-ws-route")
    public ApiResponse<WorkspaceFileRouteResponse> routeLocalDirectoryPicker(
            @PathVariable String clientInstanceId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(routingService.routeLocalDirectoryPicker(
                principal.userId(), new LocalClientInstanceId(clientInstanceId)), traceId);
    }

    /**
     * 超级管理员列出可直连的后端服务器，用于跨服务器工作空间选择器。
     */
    @GetMapping("/api/internal/platform/workspace-management/backend-servers")
    public ApiResponse<List<WorkspaceBackendServerResponse>> listBackendServers(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return ApiResponse.ok(routingService.listBackendServers(principal.userId(), "opencode", traceId), traceId);
    }

    /**
     * 在目标后端签发文件 WebSocket 一次性 ticket。
     */
    @PostMapping("/api/internal/platform/workspace-management/file-ws/tickets")
    public Mono<ApiResponse<WorkspaceFileSocketDtos.TicketResponse>> createTicket(
            @RequestBody(required = false) WorkspaceFileSocketDtos.TicketRequest request,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        WorkspaceFileSocketDtos.TicketRequest resolved = request == null
                ? new WorkspaceFileSocketDtos.TicketRequest(null, null, null)
                : request;
        // 本地 ticket 会同步读取 MyBatis 与 Redis 连接路由，必须离开 WebFlux event-loop，
        // 否则同步 Redis 等待可能占住负责回包的网络线程，最终让前端目录加载超时。
        return Mono.fromCallable(() -> {
                    DelegatedOperationContext context = shareContext(principal, shareId, traceId);
                    return ApiResponse.ok(ticketService.createTicket(principal, resolved, context, traceId), traceId);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private DelegatedOperationContext shareContext(
            AuthPrincipal principal,
            String shareId,
            String traceId) {
        if (shareId == null || shareId.isBlank()) return null;
        if (shareService == null) {
            throw new com.enterprise.testagent.common.error.PlatformException(
                    com.enterprise.testagent.common.error.ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                    "会话分享服务未配置");
        }
        return shareService.requireAccess(
                principal.userId(), new SessionShareId(shareId), false, traceId);
    }
}
