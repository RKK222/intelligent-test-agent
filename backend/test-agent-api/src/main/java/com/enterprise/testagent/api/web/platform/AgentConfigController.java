package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.workspace.AgentConfigApplicationService;
import com.enterprise.testagent.workspace.AgentConfigResponses;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
        return ok(exchange, service.publicStatus(isSuperAdmin(principal), principal.userId()));
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
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(service.localPublicRepositoryStatus(principal.userId()), RuntimeApiSupport.traceId(exchange));
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

    @PostMapping("/public/repositories/{linuxServerId}/pull")
    public ApiResponse<AgentConfigResponses.PublicRepositoryStatusResponse> pullPublicRepository(
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
                .orElseGet(() -> {
                    service.updatePublicConfig(
                            request.branch(),
                            request.operationId(),
                            Boolean.TRUE.equals(request.discardLocalChanges()),
                            principal.userId(),
                            RuntimeApiSupport.traceId(exchange));
                    return ApiResponse.ok(service.localPublicRepositoryStatus(principal.userId()), RuntimeApiSupport.traceId(exchange));
                });
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
     * 公共配置"提交并推送"复合接口：fetch 远端后提交本地修改、merge 远端分支、push 并广播同步。
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

    @GetMapping("/public/git-conflicts")
    public ApiResponse<Object> listPublicGitConflicts(
            @RequestParam(required = false) String worktreeId,
            @RequestParam(required = false) String linuxServerId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return publicConflictTarget(worktreeId, linuxServerId)
                .map(target -> routingService.forward(
                        exchange,
                        target,
                        null,
                        new TypeReference<ApiResponse<Object>>() {}))
                .orElseGet(() -> ok(exchange, new AgentConfigDtos.GitConflictFilesResponse(
                        service.publicGitConflictFiles(worktreeId, principal.userId()))));
    }

    @GetMapping("/public/git-conflict")
    public ApiResponse<Object> getPublicGitConflict(
            @RequestParam String path,
            @RequestParam(required = false) String worktreeId,
            @RequestParam(required = false) String linuxServerId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return publicConflictTarget(worktreeId, linuxServerId)
                .map(target -> routingService.forward(
                        exchange,
                        target,
                        null,
                        new TypeReference<ApiResponse<Object>>() {}))
                .orElseGet(() -> ok(exchange, service.getPublicGitConflict(path, worktreeId, principal.userId())));
    }

    @PostMapping("/public/git-conflict/resolve")
    public ApiResponse<Object> resolvePublicGitConflict(
            @RequestBody AgentConfigDtos.GitConflictRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return publicConflictTarget(request.worktreeId(), request.linuxServerId())
                .map(target -> routingService.forward(
                        exchange,
                        target,
                        request,
                        new TypeReference<ApiResponse<Object>>() {}))
                .orElseGet(() -> {
                    service.resolvePublicGitConflict(
                            request.path(),
                            request.resolution(),
                            request.content(),
                            request.worktreeId(),
                            principal.userId());
                    return ok(exchange, null);
                });
    }

    @PostMapping("/public/git-conflict/resolve-all")
    public ApiResponse<Object> resolveAllPublicGitConflicts(
            @RequestBody AgentConfigDtos.ResolveAllGitConflictsRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return publicConflictTarget(request.worktreeId(), request.linuxServerId())
                .map(target -> routingService.forward(
                        exchange,
                        target,
                        request,
                        new TypeReference<ApiResponse<Object>>() {}))
                .orElseGet(() -> {
                    service.resolveAllPublicGitConflicts(request.resolution(), request.worktreeId(), principal.userId());
                    return ok(exchange, null);
                });
    }

    @PostMapping("/public/git-conflict/abort")
    public ApiResponse<Object> abortPublicGitConflict(
            @RequestBody(required = false) AgentConfigDtos.ResolveAllGitConflictsRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        AgentConfigDtos.ResolveAllGitConflictsRequest resolved = request == null
                ? new AgentConfigDtos.ResolveAllGitConflictsRequest(null, null, null)
                : request;
        return publicConflictTarget(resolved.worktreeId(), resolved.linuxServerId())
                .map(target -> routingService.forward(
                        exchange,
                        target,
                        resolved,
                        new TypeReference<ApiResponse<Object>>() {}))
                .orElseGet(() -> {
                    service.abortPublicGitConflict(resolved.worktreeId(), principal.userId());
                    return ok(exchange, null);
                });
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
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        if (linuxServerId == null || linuxServerId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "服务器不能为空", Map.of("linuxServerId", ""));
        }
        return ApiResponse.ok(service.listPublicWorktrees(linuxServerId, principal.userId()), RuntimeApiSupport.traceId(exchange));
    }

    /**
     * 公共个人配置保存后的本人热加载入口；跨服务器先复用公共 worktree 的既有 Java 路由。
     */
    @PostMapping("/public/runtime-reload")
    public Mono<ApiResponse<Object>> reloadPublicPersonalRuntime(
            @RequestBody AgentConfigDtos.PublicRuntimeReloadRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        // 本地重载会同步等待 OpenCode dispose，必须离开 WebFlux 事件线程；跨服务器转发同样可能阻塞。
        return Mono.fromCallable(() -> publicConflictTarget(request.worktreeId(), request.linuxServerId())
                        .map(target -> routingService.forward(
                                exchange,
                                target,
                                request,
                                new TypeReference<ApiResponse<Object>>() {}))
                        .orElseGet(() -> ApiResponse.ok(service.reloadPublicPersonalRuntime(
                                request.worktreeId(),
                                principal.userId(),
                                traceId), traceId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/public/diff")
    public ApiResponse<Object> publicDiff(@RequestParam(required = false) String worktreeId, ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        var target = routingService.forwardTargetForPublicWorktree(worktreeId);
        if (target.isPresent()) {
            return routingService.forward(
                    exchange,
                    target.get(),
                    null,
                    new TypeReference<ApiResponse<Object>>() {});
        }
        return ok(exchange, service.publicDiff(worktreeId, principal.userId()));
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

    @PostMapping("/public/discard")
    public ApiResponse<Void> publicDiscard(@RequestBody AgentConfigDtos.StageRequest request, ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        var target = routingService.forwardTargetForPublicWorktree(request.worktreeId());
        if (target.isPresent()) {
            return routingService.forward(
                    exchange,
                    target.get(),
                    request,
                    new TypeReference<ApiResponse<Void>>() {});
        }
        service.publicDiscard(request.files(), request.worktreeId(), principal.userId());
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

    @PostMapping("/workspaces/{workspaceId}/worktrees")
    public ApiResponse<Object> createWorkspaceWorktree(
            @PathVariable String workspaceId,
            @RequestBody AgentConfigDtos.WorktreeRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
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
        // 差异读取对应用成员开放；提交、推送等写操作仍由 APP_ADMIN 校验。
        AuthWebSupport.getAuthPrincipal(exchange);
        return ok(exchange, service.workspaceDiff(workspaceId, worktreeId));
    }

    @PostMapping("/workspaces/{workspaceId}/stage")
    public ApiResponse<Void> workspaceStage(
            @PathVariable String workspaceId,
            @RequestBody AgentConfigDtos.StageRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        service.workspaceStage(workspaceId, request.files(), request.worktreeId(), principal.userId());
        return ApiResponse.ok(null, RuntimeApiSupport.traceId(exchange));
    }

    @PostMapping("/workspaces/{workspaceId}/unstage")
    public ApiResponse<Void> workspaceUnstage(
            @PathVariable String workspaceId,
            @RequestBody AgentConfigDtos.StageRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        service.workspaceUnstage(workspaceId, request.files(), request.worktreeId(), principal.userId());
        return ApiResponse.ok(null, RuntimeApiSupport.traceId(exchange));
    }

    @PostMapping("/workspaces/{workspaceId}/discard")
    public ApiResponse<Void> workspaceDiscard(
            @PathVariable String workspaceId,
            @RequestBody AgentConfigDtos.StageRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        service.workspaceDiscard(workspaceId, request.files(), request.worktreeId(), principal.userId());
        return ApiResponse.ok(null, RuntimeApiSupport.traceId(exchange));
    }

    @PostMapping("/workspaces/{workspaceId}/commit")
    public ApiResponse<Object> workspaceCommit(
            @PathVariable String workspaceId,
            @RequestBody AgentConfigDtos.CommitRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
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
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
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

    private java.util.Optional<String> publicConflictTarget(String worktreeId, String linuxServerId) {
        if (worktreeId != null && !worktreeId.isBlank()) {
            return routingService.forwardTargetForPublicWorktree(worktreeId);
        }
        if (linuxServerId == null || linuxServerId.isBlank()) {
            return java.util.Optional.empty();
        }
        return routingService.forwardTargetForRequestedServer(linuxServerId);
    }

}
