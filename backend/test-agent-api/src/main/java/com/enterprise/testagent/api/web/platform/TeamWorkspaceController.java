package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRouteResponse;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
import com.enterprise.testagent.system.management.team.SystemAdminTeamApplicationService;
import com.enterprise.testagent.system.management.team.SystemAdminTeamApplicationService.AuthorizedScope;
import com.enterprise.testagent.system.management.team.SystemAdminTeamApplicationService.AuthorizedTarget;
import com.enterprise.testagent.system.management.team.TeamOversightRequestContext;
import com.enterprise.testagent.workspace.TeamWorkspaceApplicationService;
import com.enterprise.testagent.workspace.TeamWorkspaceExportService;
import com.enterprise.testagent.workspace.TeamWorkspaceResponses;
import com.enterprise.testagent.workspace.TeamWorkspaceResponses.ApplicationResponse;
import com.enterprise.testagent.workspace.TeamWorkspaceResponses.CommitDetailResponse;
import com.enterprise.testagent.workspace.TeamWorkspaceResponses.CommitDiffResponse;
import com.enterprise.testagent.workspace.TeamWorkspaceResponses.CommitPageResponse;
import com.enterprise.testagent.workspace.TeamWorkspaceResponses.ContributionResponse;
import com.enterprise.testagent.workspace.TeamWorkspaceResponses.GitStatusResponse;
import com.enterprise.testagent.workspace.TeamWorkspaceResponses.WorkspaceTemplateResponse;
import com.enterprise.testagent.workspace.TeamWorkspaceResponses.WorkspaceVersionResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 系统管理员按团队范围查看应用、版本、个人 worktree 和 Git 证据的只读入口。 */
@RestController
@RequestMapping("/api/internal/platform/workspace-management/team")
public class TeamWorkspaceController {

    private final SystemAdminTeamApplicationService teams;
    private final TeamWorkspaceApplicationService workspaces;
    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final WorkspaceFileRoutingService fileRouting;
    private final WorkspaceFileSocketTicketService ticketService;
    private final TeamWorkspaceExportService exportService;
    private final TeamWorkspaceExportDownloadTicketStore exportTickets;

    public TeamWorkspaceController(
            SystemAdminTeamApplicationService teams,
            TeamWorkspaceApplicationService workspaces,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            WorkspaceFileRoutingService fileRouting,
            WorkspaceFileSocketTicketService ticketService,
            TeamWorkspaceExportService exportService,
            TeamWorkspaceExportDownloadTicketStore exportTickets) {
        this.teams = Objects.requireNonNull(teams);
        this.workspaces = Objects.requireNonNull(workspaces);
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.forwarder = Objects.requireNonNull(forwarder);
        this.fileRouting = Objects.requireNonNull(fileRouting);
        this.ticketService = Objects.requireNonNull(ticketService);
        this.exportService = Objects.requireNonNull(exportService);
        this.exportTickets = Objects.requireNonNull(exportTickets);
    }

    @GetMapping("/applications")
    public Mono<ApiResponse<List<ApplicationResponse>>> applications(
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            @RequestParam(required = false) String targetUserId,
            ServerWebExchange exchange) {
        return blocking(exchange, scopeMode, ownerUserId, "APPLICATION_LIST", "TEAM", null, targetUserId,
                scope -> workspaces.applications(scope.scope().global(), owner(scope), target(targetUserId)),
                new TypeReference<>() { });
    }

    @GetMapping("/applications/{appId}/workspaces")
    public Mono<ApiResponse<List<WorkspaceTemplateResponse>>> workspaceTemplates(
            @PathVariable String appId,
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            @RequestParam(required = false) String targetUserId,
            ServerWebExchange exchange) {
        return blocking(exchange, scopeMode, ownerUserId, "WORKSPACE_TEMPLATE_LIST", "APPLICATION", appId,
                targetUserId,
                scope -> workspaces.workspaceTemplates(scope.scope().global(), owner(scope), appId, target(targetUserId)),
                new TypeReference<>() { });
    }

    @GetMapping("/workspaces/{workspaceId}/versions")
    public Mono<ApiResponse<List<WorkspaceVersionResponse>>> versions(
            @PathVariable String workspaceId,
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            @RequestParam(required = false) String targetUserId,
            ServerWebExchange exchange) {
        return blocking(exchange, scopeMode, ownerUserId, "VERSION_LIST", "WORKSPACE_TEMPLATE", workspaceId,
                targetUserId,
                scope -> workspaces.versions(scope.scope().global(), owner(scope), workspaceId, target(targetUserId)),
                new TypeReference<>() { });
    }

    @GetMapping("/versions/{versionId}/contributions")
    public Mono<ApiResponse<List<ContributionResponse>>> contributions(
            @PathVariable String versionId,
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            ServerWebExchange exchange) {
        return blocking(exchange, scopeMode, ownerUserId, "CONTRIBUTION_LIST", "VERSION", versionId,
                scope -> workspaces.contributions(scope.scope().global(), owner(scope), versionId),
                new TypeReference<>() { });
    }

    @GetMapping("/personal-workspaces/{personalWorkspaceId}/status")
    public Mono<ApiResponse<GitStatusResponse>> status(
            @PathVariable String personalWorkspaceId,
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            ServerWebExchange exchange) {
        return personal(exchange, scopeMode, ownerUserId, personalWorkspaceId,
                "GIT_STATUS_READ", null,
                scope -> workspaces.status(scope.scope().global(), owner(scope), personalWorkspaceId),
                new TypeReference<>() { });
    }

    @GetMapping("/personal-workspaces/{personalWorkspaceId}/commits")
    public Mono<ApiResponse<CommitPageResponse>> commits(
            @PathVariable String personalWorkspaceId,
            @RequestParam(defaultValue = "PERSONAL") String kind,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            ServerWebExchange exchange) {
        requireCommitPage(offset, limit);
        return personal(exchange, scopeMode, ownerUserId, personalWorkspaceId,
                "COMMIT_LIST", null,
                scope -> workspaces.commits(scope.scope().global(), owner(scope),
                        personalWorkspaceId, kind, offset, limit),
                new TypeReference<>() { });
    }

    @GetMapping("/personal-workspaces/{personalWorkspaceId}/commits/{commit}")
    public Mono<ApiResponse<CommitDetailResponse>> commitDetail(
            @PathVariable String personalWorkspaceId,
            @PathVariable String commit,
            @RequestParam(defaultValue = "PERSONAL") String kind,
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            ServerWebExchange exchange) {
        return personal(exchange, scopeMode, ownerUserId, personalWorkspaceId,
                "COMMIT_DETAIL_READ", null,
                scope -> workspaces.commitDetail(scope.scope().global(), owner(scope),
                        personalWorkspaceId, kind, commit),
                new TypeReference<>() { });
    }

    @GetMapping("/personal-workspaces/{personalWorkspaceId}/commits/{commit}/diff")
    public Mono<ApiResponse<CommitDiffResponse>> commitDiff(
            @PathVariable String personalWorkspaceId,
            @PathVariable String commit,
            @RequestParam String path,
            @RequestParam(defaultValue = "PERSONAL") String kind,
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            ServerWebExchange exchange) {
        return personal(exchange, scopeMode, ownerUserId, personalWorkspaceId,
                "COMMIT_DIFF_READ", path,
                scope -> workspaces.commitDiff(scope.scope().global(), owner(scope),
                        personalWorkspaceId, kind, commit, path),
                new TypeReference<>() { });
    }

    /** 返回个人 worktree 权威节点，浏览器随后直接在该节点申请 ticket。 */
    @PostMapping("/personal-workspaces/{personalWorkspaceId}/file-ws-route")
    public Mono<ApiResponse<WorkspaceFileRouteResponse>> fileRoute(
            @PathVariable String personalWorkspaceId,
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    AuthorizedScope scope = authorize(exchange, scopeMode, ownerUserId);
                    var personal = workspaces.personalWorkspace(
                            scope.scope().global(), owner(scope), personalWorkspaceId);
                    AuthorizedTarget target = teams.authorizeTarget(
                            scope.actor().user().userId(), scope.scope().mode(), scope.scope().ownerUserId(),
                            personal.workspace().userId());
                    WorkspaceFileRouteResponse result = fileRouting.routeSupportWorkspace(
                            personal.workspace().runtimeWorkspaceId());
                    teams.recordOutcome(
                            target.actor(), target.target(), "FILE_ROUTE_READ", "PERSONAL_WORKSPACE",
                            personalWorkspaceId, null, "SUCCESS", null, requestContext(exchange));
                    return ApiResponse.ok(result, RuntimeApiSupport.traceId(exchange));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 在 file route 指向的目标 Java 上签发一次性团队只读 ticket。 */
    @PostMapping("/personal-workspaces/{personalWorkspaceId}/file-ws-tickets")
    public Mono<ApiResponse<WorkspaceFileSocketDtos.TicketResponse>> fileTicket(
            @PathVariable String personalWorkspaceId,
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            @RequestBody TeamFileTicketRequest request,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SYSTEM_ADMIN);
                    var result = ticketService.createTeamReadOnlyTicket(
                            principal, scopeMode, ownerUserId, personalWorkspaceId,
                            request == null ? null : request.linuxServerId(), requestContext(exchange));
                    return ApiResponse.ok(result, RuntimeApiSupport.traceId(exchange));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/versions/{versionId}/exports")
    public Mono<ApiResponse<TeamWorkspaceResponses.ExportResponse>> createExport(
            @PathVariable String versionId,
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    AuthorizedScope scope = authorize(exchange, scopeMode, ownerUserId);
                    var result = exportService.create(
                            scope.scope().mode(), scope.actor().user().userId(), owner(scope),
                            scope.scope().global(), versionId);
                    teams.recordOutcome(scope.actor(), null, "EXPORT_CREATED", "VERSION", versionId,
                            null, "SUCCESS", null, requestContext(exchange));
                    return ApiResponse.ok(result, RuntimeApiSupport.traceId(exchange));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/exports/{exportId}")
    public Mono<ApiResponse<TeamWorkspaceResponses.ExportResponse>> exportProgress(
            @PathVariable String exportId,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SYSTEM_ADMIN);
                    var access = exportService.access(exportId, principal.userId().value());
                    if (!alreadyRouted(exchange)) {
                        var remote = routeResolver.remoteTarget(access.coordinatorLinuxServerId());
                        if (remote.isPresent()) {
                            return forwarder.forwardTyped(
                                    exchange, routeResolver.requireBackend(remote.get()), null,
                                    new TypeReference<ApiResponse<TeamWorkspaceResponses.ExportResponse>>() { });
                        }
                    }
                    AuthorizedScope scope = authorizeExport(principal, access, exportId);
                    var result = exportService.get(exportId, principal.userId().value());
                    teams.recordOutcome(scope.actor(), null, "EXPORT_PROGRESS_READ", "TEAM_EXPORT", exportId,
                            null, "SUCCESS", null, requestContext(exchange));
                    return ApiResponse.ok(result, RuntimeApiSupport.traceId(exchange));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/exports/{exportId}/cancel")
    public Mono<ApiResponse<Boolean>> cancelExport(
            @PathVariable String exportId,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SYSTEM_ADMIN);
                    var access = exportService.access(exportId, principal.userId().value());
                    if (!alreadyRouted(exchange)) {
                        var remote = routeResolver.remoteTarget(access.coordinatorLinuxServerId());
                        if (remote.isPresent()) {
                            return forwarder.forwardTyped(
                                    exchange, routeResolver.requireBackend(remote.get()), null,
                                    new TypeReference<ApiResponse<Boolean>>() { });
                        }
                    }
                    AuthorizedScope scope = authorizeExport(principal, access, exportId);
                    boolean result = exportService.cancel(exportId, principal.userId().value());
                    teams.recordOutcome(scope.actor(), null, "EXPORT_CANCELLED", "TEAM_EXPORT", exportId,
                            null, "SUCCESS", null, requestContext(exchange));
                    return ApiResponse.ok(result, RuntimeApiSupport.traceId(exchange));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 浏览器使用返回的目标节点 baseUrl 和一次性 ticket 原生流式下载。 */
    @PostMapping("/exports/{exportId}/download-route")
    public Mono<ApiResponse<ExportDownloadRouteResponse>> exportDownloadRoute(
            @PathVariable String exportId,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SYSTEM_ADMIN);
                    var access = exportService.access(exportId, principal.userId().value());
                    if (!alreadyRouted(exchange)) {
                        var remote = routeResolver.remoteTarget(access.coordinatorLinuxServerId());
                        if (remote.isPresent()) {
                            return forwarder.forwardTyped(
                                    exchange, routeResolver.requireBackend(remote.get()), null,
                                    new TypeReference<ApiResponse<ExportDownloadRouteResponse>>() { });
                        }
                    }
                    AuthorizedScope scope = authorizeExport(principal, access, exportId);
                    exportService.artifact(exportId, principal.userId().value());
                    var ticket = exportTickets.issue(
                            exportId, principal.userId().value(), access.scopeMode(),
                            access.ownerUserId(), access.targetUserIds());
                    BackendJavaProcess backend = routeResolver.requireBackend(access.coordinatorLinuxServerId());
                    teams.recordOutcome(scope.actor(), null, "EXPORT_DOWNLOAD_ROUTE", "TEAM_EXPORT", exportId,
                            null, "SUCCESS", null, requestContext(exchange));
                    return ApiResponse.ok(new ExportDownloadRouteResponse(
                            backend.listenUrl(), "/api/internal/platform/workspace-management/team/exports/"
                            + exportId + "/download?ticket=" + ticket.value(), ticket.expiresAt()),
                            RuntimeApiSupport.traceId(exchange));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/exports/{exportId}/download")
    public Mono<Void> downloadExport(
            @PathVariable String exportId,
            @RequestParam String ticket,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    var token = exportTickets.consume(ticket);
                    if (!token.exportId().equals(exportId)) {
                        throw new PlatformException(ErrorCode.FORBIDDEN, "团队导出下载范围不匹配");
                    }
                    try {
                        for (String targetUserId : token.targetUserIds()) {
                            teams.authorizeTarget(
                                    new UserId(token.actorUserId()), token.scopeMode(), token.ownerUserId(),
                                    new UserId(targetUserId));
                        }
                    } catch (RuntimeException exception) {
                        exportService.invalidateAuthorization(exportId);
                        throw exception;
                    }
                    return exportService.artifact(exportId, token.actorUserId());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> writeDownload(exchange, path));
    }

    private <T> Mono<ApiResponse<T>> blocking(
            ServerWebExchange exchange,
            TeamScopeMode mode,
            String ownerUserId,
            String action,
            String resourceType,
            String resourceId,
            java.util.function.Function<AuthorizedScope, T> actionBody,
            TypeReference<ApiResponse<T>> ignored) {
        return blocking(exchange, mode, ownerUserId, action, resourceType, resourceId, null, actionBody, ignored);
    }

    /**
     * 成员上下文查询在返回前实时复核角色和团队归属，并把目标用户写入审计。
     * 不传 targetUserId 时审计目标保持为空，列表仍是原团队级结果。
     */
    private <T> Mono<ApiResponse<T>> blocking(
            ServerWebExchange exchange,
            TeamScopeMode mode,
            String ownerUserId,
            String action,
            String resourceType,
            String resourceId,
            String targetUserId,
            java.util.function.Function<AuthorizedScope, T> actionBody,
            TypeReference<ApiResponse<T>> ignored) {
        return Mono.fromCallable(() -> {
                    AuthorizedScope scope = authorize(exchange, mode, ownerUserId);
                    var targetUser = targetUserId == null || targetUserId.isBlank()
                            ? null
                            : teams.authorizeTarget(
                                    scope.actor().user().userId(), scope.scope().mode(), scope.scope().ownerUserId(),
                                    new UserId(targetUserId)).target();
                    T result = actionBody.apply(scope);
                    teams.recordOutcome(scope.actor(), targetUser, action, resourceType, resourceId, null,
                            "SUCCESS", null, requestContext(exchange));
                    return ApiResponse.ok(result, RuntimeApiSupport.traceId(exchange));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private UserId target(String targetUserId) {
        return targetUserId == null || targetUserId.isBlank() ? null : new UserId(targetUserId);
    }

    private <T> Mono<ApiResponse<T>> personal(
            ServerWebExchange exchange,
            TeamScopeMode mode,
            String ownerUserId,
            String personalWorkspaceId,
            String action,
            String path,
            java.util.function.Function<AuthorizedScope, T> localAction,
            TypeReference<ApiResponse<T>> responseType) {
        return Mono.fromCallable(() -> {
                    AuthorizedScope scope = authorize(exchange, mode, ownerUserId);
                    var personal = workspaces.personalWorkspace(
                            scope.scope().global(), owner(scope), personalWorkspaceId);
                    AuthorizedTarget target = teams.authorizeTarget(
                            scope.actor().user().userId(), scope.scope().mode(), scope.scope().ownerUserId(),
                            personal.workspace().userId());
                    if (!alreadyRouted(exchange)) {
                        var remote = routeResolver.remoteTarget(personal.linuxServerId());
                        if (remote.isPresent()) {
                            BackendJavaProcess backend = routeResolver.requireBackend(remote.get());
                            return forwarder.forwardTyped(exchange, backend, null, responseType);
                        }
                    }
                    T result = localAction.apply(scope);
                    teams.recordOutcome(target.actor(), target.target(), action, "PERSONAL_WORKSPACE",
                            personalWorkspaceId, path, "SUCCESS", null, requestContext(exchange));
                    return ApiResponse.ok(result, RuntimeApiSupport.traceId(exchange));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private AuthorizedScope authorize(ServerWebExchange exchange, TeamScopeMode mode, String ownerUserId) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SYSTEM_ADMIN);
        return teams.authorizeScope(principal, mode, ownerUserId);
    }

    private UserId owner(AuthorizedScope scope) {
        return scope.scope().global() ? null : new UserId(scope.scope().ownerUserId());
    }

    private boolean alreadyRouted(ServerWebExchange exchange) {
        return "true".equalsIgnoreCase(exchange.getRequest().getHeaders()
                .getFirst(BackendHttpForwarder.ROUTED_HEADER));
    }

    private void requireCommitPage(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "提交分页参数无效");
        }
    }

    private void requireTargets(AuthorizedScope scope, List<String> targetUserIds) {
        for (String targetUserId : targetUserIds) {
            teams.authorizeTarget(
                    scope.actor().user().userId(), scope.scope().mode(), scope.scope().ownerUserId(),
                    new UserId(targetUserId));
        }
    }

    /** 任何实时授权失败都会在产物所在协调节点立即作废并清理该导出。 */
    private AuthorizedScope authorizeExport(
            AuthPrincipal principal,
            TeamWorkspaceExportService.ExportAccess access,
            String exportId) {
        try {
            AuthorizedScope scope = teams.authorizeScope(principal, access.scopeMode(), access.ownerUserId());
            requireTargets(scope, access.targetUserIds());
            return scope;
        } catch (RuntimeException exception) {
            exportService.invalidateAuthorization(exportId);
            throw exception;
        }
    }

    private Mono<Void> writeDownload(ServerWebExchange exchange, Path path) {
        try {
            exchange.getResponse().getHeaders().setContentType(MediaType.parseMediaType("application/zip"));
            exchange.getResponse().getHeaders().setContentDisposition(
                    ContentDisposition.attachment().filename(path.getFileName().toString()).build());
            exchange.getResponse().getHeaders().setContentLength(Files.size(path));
            exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
            return exchange.getResponse().writeWith(
                    DataBufferUtils.read(path, exchange.getResponse().bufferFactory(), 256 * 1024));
        } catch (java.io.IOException exception) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "团队导出产物不存在", java.util.Map.of(), exception);
        }
    }

    private TeamOversightRequestContext requestContext(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getRemoteAddress() == null
                ? null : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return new TeamOversightRequestContext(
                RuntimeApiSupport.traceId(exchange), ip,
                exchange.getRequest().getHeaders().getFirst("User-Agent"));
    }

    public record TeamFileTicketRequest(String linuxServerId) {
    }

    public record ExportDownloadRouteResponse(String baseUrl, String downloadPath, java.time.Instant expiresAt) {
    }
}
