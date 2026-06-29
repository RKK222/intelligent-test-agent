package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.workspace.AgentConfigApplicationService;
import com.icbc.testagent.workspace.AgentConfigResponses;
import com.icbc.testagent.workspace.FileContentResponse;
import com.icbc.testagent.workspace.FileTreeEntryResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * Agent 配置 HTTP Controller：公共/工作空间文件、Git 操作和进度 ticket 入口。
 */
@RestController
@RequestMapping("/api/internal/platform/workspace-management/agent-config")
public class AgentConfigController {

    private final AgentConfigApplicationService service;
    private final AgentConfigOperationTicketService ticketService;
    private final AgentConfigBackendRoutingService routingService;
    private final AgentConfigFileRoutingService fileRoutingService;

    public AgentConfigController(
            AgentConfigApplicationService service,
            AgentConfigOperationTicketService ticketService) {
        this(service, ticketService, new AgentConfigBackendRoutingService(service), null);
    }

    @Autowired
    public AgentConfigController(
            AgentConfigApplicationService service,
            AgentConfigOperationTicketService ticketService,
            AgentConfigBackendRoutingService routingService,
            AgentConfigFileRoutingService fileRoutingService) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.ticketService = Objects.requireNonNull(ticketService, "ticketService must not be null");
        this.routingService = Objects.requireNonNull(routingService, "routingService must not be null");
        this.fileRoutingService = fileRoutingService;
    }

    @GetMapping("/public/status")
    public ApiResponse<Object> publicStatus(ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ok(exchange, service.publicStatus(isSuperAdmin(principal)));
    }

    @GetMapping("/workspaces/{workspaceId}/status")
    public ApiResponse<Object> workspaceStatus(@PathVariable String workspaceId, ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ok(exchange, service.workspaceStatus(workspaceId, isSuperAdmin(principal)));
    }

    @GetMapping("/public/branches")
    public ApiResponse<List<String>> publicBranches(ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(service.publicBranches(principal.userId()), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/public/repositories")
    public ApiResponse<List<AgentConfigResponses.PublicRepositoryStatusResponse>> publicRepositories(ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(routingService.listPublicRepositories(exchange, traceId), traceId);
    }

    @GetMapping("/public/repositories/local")
    public ApiResponse<AgentConfigResponses.PublicRepositoryStatusResponse> localPublicRepository(ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(service.localPublicRepositoryStatus(), RuntimeApiSupport.traceId(exchange));
    }

    @PostMapping("/public/repositories/{linuxServerId}/initialize")
    public ApiResponse<AgentConfigResponses.PublicRepositoryStatusResponse> initializePublicRepository(
            @PathVariable String linuxServerId,
            @RequestBody AgentConfigDtos.BranchRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return routingService.forwardTargetForRequestedServer(linuxServerId)
                .map(target -> routingService.forward(
                        exchange,
                        target,
                        request,
                        new TypeReference<ApiResponse<AgentConfigResponses.PublicRepositoryStatusResponse>>() {}))
                .orElseGet(() -> ApiResponse.ok(service.initializeLocalPublicRepository(
                        request.branch(),
                        request.operationId(),
                        principal.userId(),
                        RuntimeApiSupport.traceId(exchange)), RuntimeApiSupport.traceId(exchange)));
    }

    @PostMapping("/public/update")
    public ApiResponse<Object> updatePublic(
            @RequestBody AgentConfigDtos.BranchRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return ok(exchange, service.updatePublicConfig(
                request.branch(),
                request.operationId(),
                Boolean.TRUE.equals(request.discardLocalChanges()),
                principal.userId(),
                RuntimeApiSupport.traceId(exchange)));
    }

    /**
     * 公共配置"更新 + 提交并推送"复合接口：按分支拉取最新后 stage 工作区全部变更并用 commitMessage 生成一次提交，最后 push 到远端并广播同步。
     */
    @PostMapping("/public/update-and-push")
    public ApiResponse<Object> updatePublicAndPush(
            @RequestBody AgentConfigDtos.UpdatePublicConfigAndPushRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return ok(exchange, service.updatePublicConfigAndPush(
                request.branch(),
                request.commitMessage(),
                request.operationId(),
                Boolean.TRUE.equals(request.discardLocalChanges()),
                principal.userId(),
                RuntimeApiSupport.traceId(exchange)));
    }

    @PostMapping("/file-ws-route")
    public ApiResponse<AgentConfigDtos.FileRouteResponse> fileWebSocketRoute(
            @RequestBody(required = false) AgentConfigDtos.FileRouteRequest request,
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        if (fileRoutingService == null) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "Agent 配置文件 WebSocket 路由不可用");
        }
        return ApiResponse.ok(fileRoutingService.route(request), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/public/files")
    public ApiResponse<List<FileTreeEntryResponse>> listPublicFiles(
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String worktreeId,
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        var target = routingService.forwardTargetForPublicWorktree(worktreeId);
        if (target.isPresent()) {
            throw fileWebSocketRequired(target.get());
        }
        return ApiResponse.ok(service.listPublicAgentFiles(path, worktreeId), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/public/files/content")
    public ApiResponse<FileContentResponse> readPublicFile(
            @RequestParam String path,
            @RequestParam(required = false) String worktreeId,
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        var target = routingService.forwardTargetForPublicWorktree(worktreeId);
        if (target.isPresent()) {
            throw fileWebSocketRequired(target.get());
        }
        return ApiResponse.ok(service.readPublicAgentFile(path, worktreeId), RuntimeApiSupport.traceId(exchange));
    }

    @PutMapping("/public/files/content")
    public ApiResponse<Void> writePublicFile(
            @Valid @RequestBody AgentConfigDtos.FileContentRequest request,
            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        var target = routingService.forwardTargetForPublicWorktree(request.worktreeId());
        if (target.isPresent()) {
            throw fileWebSocketRequired(target.get());
        }
        service.writePublicAgentFile(request.path(), request.content(), request.worktreeId());
        return ApiResponse.ok(null, RuntimeApiSupport.traceId(exchange));
    }

    @PostMapping("/public/worktrees")
    public ApiResponse<Object> createPublicWorktree(
            @RequestBody AgentConfigDtos.WorktreeRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return routingService.forwardTargetForRequestedServer(request.linuxServerId())
                .map(target -> routingService.forward(
                        exchange,
                        target,
                        request,
                        new TypeReference<ApiResponse<Object>>() {}))
                .orElseGet(() -> ok(exchange, service.createPublicWorktree(
                        request.baseName(),
                        request.branch(),
                        request.operationId(),
                        request.linuxServerId(),
                        principal.userId(),
                        RuntimeApiSupport.traceId(exchange))));
    }

    @GetMapping("/public/worktrees")
    public ApiResponse<List<AgentConfigResponses.AgentConfigWorktreeOptionResponse>> publicWorktrees(
            @RequestParam(required = false) String linuxServerId,
            ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        if (linuxServerId == null || linuxServerId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "服务器不能为空", Map.of("linuxServerId", ""));
        }
        return ApiResponse.ok(service.listPublicWorktrees(linuxServerId), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/public/diff")
    public ApiResponse<Object> publicDiff(@RequestParam(required = false) String worktreeId, ServerWebExchange exchange) {
        AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        var target = routingService.forwardTargetForPublicWorktree(worktreeId);
        if (target.isPresent()) {
            return routingService.forward(
                    exchange,
                    target.get(),
                    null,
                    new TypeReference<ApiResponse<Object>>() {});
        }
        return ok(exchange, service.publicDiff(worktreeId));
    }

    @PostMapping("/public/stage")
    public ApiResponse<Void> publicStage(@RequestBody AgentConfigDtos.StageRequest request, ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        var target = routingService.forwardTargetForPublicWorktree(request.worktreeId());
        if (target.isPresent()) {
            return routingService.forward(
                    exchange,
                    target.get(),
                    request,
                    new TypeReference<ApiResponse<Void>>() {});
        }
        service.publicStage(request.files(), request.worktreeId(), principal.userId());
        return ApiResponse.ok(null, RuntimeApiSupport.traceId(exchange));
    }

    @PostMapping("/public/unstage")
    public ApiResponse<Void> publicUnstage(@RequestBody AgentConfigDtos.StageRequest request, ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        var target = routingService.forwardTargetForPublicWorktree(request.worktreeId());
        if (target.isPresent()) {
            return routingService.forward(
                    exchange,
                    target.get(),
                    request,
                    new TypeReference<ApiResponse<Void>>() {});
        }
        service.publicUnstage(request.files(), request.worktreeId(), principal.userId());
        return ApiResponse.ok(null, RuntimeApiSupport.traceId(exchange));
    }

    @PostMapping("/public/commit")
    public ApiResponse<Object> publicCommit(@RequestBody AgentConfigDtos.CommitRequest request, ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        var target = routingService.forwardTargetForPublicWorktree(request.worktreeId());
        if (target.isPresent()) {
            return routingService.forward(
                    exchange,
                    target.get(),
                    request,
                    new TypeReference<ApiResponse<Object>>() {});
        }
        return ok(exchange, service.publicCommit(
                request.message(),
                request.worktreeId(),
                request.operationId(),
                principal.userId(),
                RuntimeApiSupport.traceId(exchange)));
    }

    @PostMapping("/public/publish")
    public ApiResponse<Object> publicPublish(@RequestBody AgentConfigDtos.PublishRequest request, ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        var target = routingService.forwardTargetForPublicWorktree(request.worktreeId());
        if (target.isPresent()) {
            return routingService.forward(
                    exchange,
                    target.get(),
                    request,
                    new TypeReference<ApiResponse<Object>>() {});
        }
        return ok(exchange, service.publicPublish(
                request.worktreeId(),
                request.operationId(),
                principal.userId(),
                RuntimeApiSupport.traceId(exchange)));
    }

    @GetMapping("/workspaces/{workspaceId}/files")
    public ApiResponse<List<FileTreeEntryResponse>> listWorkspaceFiles(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String worktreeId,
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(service.listWorkspaceAgentFiles(workspaceId, path, worktreeId), RuntimeApiSupport.traceId(exchange));
    }

    @GetMapping("/workspaces/{workspaceId}/files/content")
    public ApiResponse<FileContentResponse> readWorkspaceFile(
            @PathVariable String workspaceId,
            @RequestParam String path,
            @RequestParam(required = false) String worktreeId,
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(service.readWorkspaceAgentFile(workspaceId, path, worktreeId), RuntimeApiSupport.traceId(exchange));
    }

    @PutMapping("/workspaces/{workspaceId}/files/content")
    public ApiResponse<Void> writeWorkspaceFile(
            @PathVariable String workspaceId,
            @RequestBody AgentConfigDtos.FileContentRequest request,
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        service.writeWorkspaceAgentFile(workspaceId, request.path(), request.content(), request.worktreeId());
        return ApiResponse.ok(null, RuntimeApiSupport.traceId(exchange));
    }

    @PostMapping("/workspaces/{workspaceId}/worktrees")
    public ApiResponse<Object> createWorkspaceWorktree(
            @PathVariable String workspaceId,
            @RequestBody AgentConfigDtos.WorktreeRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ok(exchange, service.createWorkspaceWorktree(
                workspaceId,
                request.baseName(),
                request.branch(),
                request.operationId(),
                principal.userId(),
                RuntimeApiSupport.traceId(exchange)));
    }

    @GetMapping("/workspaces/{workspaceId}/diff")
    public ApiResponse<Object> workspaceDiff(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String worktreeId,
            ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        return ok(exchange, service.workspaceDiff(workspaceId, worktreeId));
    }

    @PostMapping("/workspaces/{workspaceId}/stage")
    public ApiResponse<Void> workspaceStage(
            @PathVariable String workspaceId,
            @RequestBody AgentConfigDtos.StageRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        service.workspaceStage(workspaceId, request.files(), request.worktreeId(), principal.userId());
        return ApiResponse.ok(null, RuntimeApiSupport.traceId(exchange));
    }

    @PostMapping("/workspaces/{workspaceId}/unstage")
    public ApiResponse<Void> workspaceUnstage(
            @PathVariable String workspaceId,
            @RequestBody AgentConfigDtos.StageRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        service.workspaceUnstage(workspaceId, request.files(), request.worktreeId(), principal.userId());
        return ApiResponse.ok(null, RuntimeApiSupport.traceId(exchange));
    }

    @PostMapping("/workspaces/{workspaceId}/commit")
    public ApiResponse<Object> workspaceCommit(
            @PathVariable String workspaceId,
            @RequestBody AgentConfigDtos.CommitRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ok(exchange, service.workspaceCommit(
                workspaceId,
                request.message(),
                request.worktreeId(),
                request.operationId(),
                principal.userId(),
                RuntimeApiSupport.traceId(exchange)));
    }

    @PostMapping("/workspaces/{workspaceId}/publish")
    public ApiResponse<Object> workspacePublish(
            @PathVariable String workspaceId,
            @RequestBody AgentConfigDtos.PublishRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ok(exchange, service.workspacePublish(
                workspaceId,
                request.worktreeId(),
                request.operationId(),
                principal.userId(),
                RuntimeApiSupport.traceId(exchange)));
    }

    @GetMapping("/operations/{operationId}")
    public ApiResponse<Object> operation(@PathVariable String operationId, ServerWebExchange exchange) {
        AuthWebSupport.getAuthPrincipal(exchange);
        return ok(exchange, service.findOperation(operationId).orElse(null));
    }

    @PostMapping("/operations/{operationId}/tickets")
    public ApiResponse<AgentConfigDtos.TicketResponse> createProgressTicket(
            @PathVariable String operationId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(
                ticketService.createTicket(principal, operationId, RuntimeApiSupport.traceId(exchange)),
                RuntimeApiSupport.traceId(exchange));
    }

    private boolean isSuperAdmin(AuthPrincipal principal) {
        return AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN);
    }

    private ApiResponse<Object> ok(ServerWebExchange exchange, Object data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }

    private PlatformException fileWebSocketRequired(String linuxServerId) {
        return new PlatformException(
                ErrorCode.CONFLICT,
                "Agent 配置文件操作请使用文件 WebSocket",
                Map.of("linuxServerId", linuxServerId, "webSocketRequired", true));
    }
}
