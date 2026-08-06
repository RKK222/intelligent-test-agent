package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.supportaccess.SupportAccessAuditQuery;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.event.RunEventSsePayload;
import com.enterprise.testagent.event.RunEventSseStreamService;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRouteResponse;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
import com.enterprise.testagent.opencode.runtime.run.RunHistoryRecoveryResult;
import com.enterprise.testagent.opencode.runtime.run.RunHistoryRecoverySource;
import com.enterprise.testagent.opencode.runtime.run.RunMessageRecoveryService;
import com.enterprise.testagent.opencode.runtime.session.SessionApplicationService;
import com.enterprise.testagent.system.supportaccess.SupportAccessApplicationService;
import com.enterprise.testagent.system.supportaccess.SupportAccessAuthorization;
import com.enterprise.testagent.system.supportaccess.SupportAccessGrantIssue;
import com.enterprise.testagent.system.supportaccess.SupportAccessRequestContext;
import com.enterprise.testagent.workspace.UserWorkspaceQueryService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 超级管理员问题排查只读入口。actor 保持当前管理员身份，target 只作为查询范围。
 */
@RestController
@RequestMapping("/api/internal/platform/system-management/support-access")
public class SupportAccessController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupportAccessController.class);

    private final SupportAccessApplicationService supportAccessService;
    private final SessionApplicationService sessionService;
    private final RunMessageRecoveryService messageRecoveryService;
    private final RunEventSseStreamService eventStreamService;
    private final UserWorkspaceQueryService userWorkspaceQueryService;
    private final WorkspaceFileRoutingService fileRoutingService;
    private final WorkspaceFileSocketTicketService ticketService;
    private final BackendJavaRouteResolver routeResolver;
    private final ManagedWorkspacePathResolver pathResolver;

    public SupportAccessController(
            SupportAccessApplicationService supportAccessService,
            SessionApplicationService sessionService,
            RunMessageRecoveryService messageRecoveryService,
            RunEventSseStreamService eventStreamService,
            UserWorkspaceQueryService userWorkspaceQueryService,
            WorkspaceFileRoutingService fileRoutingService,
            WorkspaceFileSocketTicketService ticketService,
            BackendJavaRouteResolver routeResolver,
            ManagedWorkspacePathResolver pathResolver) {
        this.supportAccessService = supportAccessService;
        this.sessionService = sessionService;
        this.messageRecoveryService = messageRecoveryService;
        this.eventStreamService = eventStreamService;
        this.userWorkspaceQueryService = userWorkspaceQueryService;
        this.fileRoutingService = fileRoutingService;
        this.ticketService = ticketService;
        this.routeResolver = routeResolver;
        this.pathResolver = pathResolver;
    }

    /** 签发绑定当前登录会话的限时只读授权；不接受共享激活暗号。 */
    @PostMapping("/grants")
    public ApiResponse<SupportAccessDtos.GrantResponse> issueGrant(
            @Valid @RequestBody SupportAccessDtos.IssueGrantRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        SupportAccessRequestContext context = requestContext(exchange);
        SupportAccessGrantIssue issue = supportAccessService.issue(
                principal,
                request.incidentId(),
                request.reason(),
                request.durationMinutes(),
                request.readOnlyAcknowledged(),
                context);
        return ApiResponse.ok(
                new SupportAccessDtos.GrantResponse(issue.grantId(), issue.grantToken(), issue.expiresAt()),
                context.traceId());
    }

    /**
     * 返回本次排查使用的新单号；旧 recent-incident 路径仅保留 HTTP 兼容，不再读取历史授权。
     */
    @GetMapping({"/grants/incident-suggestion", "/grants/recent-incident"})
    public ApiResponse<SupportAccessDtos.IncidentSuggestionResponse> getIncidentSuggestion(
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        String traceId = RuntimeApiSupport.traceId(exchange);
        String incidentId = supportAccessService.generateIncidentId(principal);
        return ApiResponse.ok(new SupportAccessDtos.IncidentSuggestionResponse(incidentId, "GENERATED"), traceId);
    }

    /** 主动撤销授权。 */
    @DeleteMapping("/grants/{grantId}")
    public ApiResponse<Void> revokeGrant(
            @PathVariable String grantId,
            @RequestHeader(name = SupportAccessApplicationService.HEADER_NAME, required = false) String grantToken,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        SupportAccessRequestContext context = requestContext(exchange);
        supportAccessService.revoke(principal, grantToken, grantId, context);
        return ApiResponse.ok(null, context.traceId());
    }

    /** 显式记录目标切换，并返回目标快照供页面 banner 展示。 */
    @PostMapping("/targets/{targetUserId}/selections")
    public ApiResponse<SupportAccessDtos.TargetResponse> selectTarget(
            @PathVariable String targetUserId,
            @RequestHeader(name = SupportAccessApplicationService.HEADER_NAME, required = false) String grantToken,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        SupportAccessRequestContext context = requestContext(exchange);
        SupportAccessAuthorization authorization = authorize(
                principal, grantToken, targetUserId, "TARGET_SELECTED", "USER", targetUserId, context);
        supportAccessService.recordTargetSelection(authorization, context);
        return ApiResponse.ok(SupportAccessDtos.TargetResponse.from(authorization.target()), context.traceId());
    }

    /** 分页读取目标用户归因会话。 */
    @GetMapping("/targets/{targetUserId}/sessions")
    public ApiResponse<PageResponse<RuntimeDtos.SessionResponse>> listSessions(
            @PathVariable String targetUserId,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(name = SupportAccessApplicationService.HEADER_NAME, required = false) String grantToken,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        SupportAccessRequestContext context = requestContext(exchange);
        UserId target = new UserId(targetUserId);
        SupportAccessAuthorization authorization = authorize(
                principal, grantToken, targetUserId, "SESSION_LIST", "USER", targetUserId, context);
        var result = supportAccessService.executeRead(
                authorization,
                "SESSION_LIST",
                "USER",
                targetUserId,
                null,
                context,
                () -> RuntimeDtos.sessionHistoryPage(sessionService.listUserSessions(
                        target, query, includeArchived, RuntimeApiSupport.pageRequest(page, size))));
        return ApiResponse.ok(result, context.traceId());
    }

    /** 读取目标用户会话的可恢复消息树，并返回 FULL/SUMMARY/LEGACY 与回放可用性元数据。 */
    @GetMapping("/targets/{targetUserId}/sessions/{sessionId}/session-tree/messages")
    public Mono<ApiResponse<RuntimeDtos.SessionTreeMessagesResponse>> getSessionTreeMessages(
            @PathVariable String targetUserId,
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived,
            @RequestHeader(name = SupportAccessApplicationService.HEADER_NAME, required = false) String grantToken,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        SupportAccessRequestContext context = requestContext(exchange);
        UserId target = new UserId(targetUserId);
        SessionId requestedSession = new SessionId(sessionId);
        SupportAccessAuthorization authorization = authorize(
                principal, grantToken, targetUserId, "SESSION_TREE_READ", "SESSION", sessionId, context);
        return Mono.fromCallable(() -> supportAccessService.executeRead(
                        authorization,
                        "SESSION_TREE_READ",
                        "SESSION",
                        sessionId,
                        null,
                        context,
                        () -> {
                            var session = sessionService.getSession(target, requestedSession, includeArchived);
                            var workspace = userWorkspaceQueryService.requireUserWorkspace(
                                    target, session.workspaceId());
                            Mono<RunHistoryRecoveryResult> recoverySource = workspaceBackendOnline(
                                    workspace.linuxServerId(), context.traceId())
                                            ? messageRecoveryService.recoverSessionTreeHistory(
                                                    requestedSession, context.traceId())
                                            : messageRecoveryService.recoverPersistedSessionTreeHistory(
                                                    requestedSession, context.traceId());
                            RunHistoryRecoveryResult recovery = recoverySource
                                    .block(Duration.ofSeconds(30));
                            List<RunEventSsePayload> events = new ArrayList<>(recovery.events());
                            if (recovery.source() == RunHistoryRecoverySource.OPENCODE) {
                                events.addAll(durableSnapshotPayloadsByRootSessionId(recovery.events()));
                            }
                            return RuntimeDtos.SessionTreeMessagesResponse.from(
                                    sessionId,
                                    events,
                                    recovery.historyRepresentation(),
                                    recovery.replayAvailable(),
                                    recovery.detailsAvailableUntil());
                        }))
                .map(result -> ApiResponse.ok(result, context.traceId()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 分页读取目标用户关联工作区。 */
    @GetMapping("/targets/{targetUserId}/workspaces")
    public ApiResponse<PageResponse<SupportAccessDtos.WorkspaceResponse>> listWorkspaces(
            @PathVariable String targetUserId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(name = SupportAccessApplicationService.HEADER_NAME, required = false) String grantToken,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        SupportAccessRequestContext context = requestContext(exchange);
        UserId target = new UserId(targetUserId);
        SupportAccessAuthorization authorization = authorize(
                principal, grantToken, targetUserId, "WORKSPACE_LIST", "USER", targetUserId, context);
        var result = supportAccessService.executeRead(
                authorization,
                "WORKSPACE_LIST",
                "USER",
                targetUserId,
                null,
                context,
                () -> supportWorkspacePage(
                        userWorkspaceQueryService.listUserWorkspaces(
                                target, RuntimeApiSupport.pageRequest(page, size)),
                        context.traceId()));
        return ApiResponse.ok(result, context.traceId());
    }

    /** 定位目标工作区的权威后端；不复用 actor 的 opencode 进程归属。 */
    @PostMapping("/targets/{targetUserId}/workspaces/{workspaceId}/file-ws-route")
    public ApiResponse<WorkspaceFileRouteResponse> routeWorkspace(
            @PathVariable String targetUserId,
            @PathVariable String workspaceId,
            @RequestHeader(name = SupportAccessApplicationService.HEADER_NAME, required = false) String grantToken,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        SupportAccessRequestContext context = requestContext(exchange);
        UserId target = new UserId(targetUserId);
        WorkspaceId workspace = new WorkspaceId(workspaceId);
        SupportAccessAuthorization authorization = authorize(
                principal, grantToken, targetUserId, "FILE_ROUTE_READ", "WORKSPACE", workspaceId, context);
        WorkspaceFileRouteResponse result = supportAccessService.executeRead(
                authorization,
                "FILE_ROUTE_READ",
                "WORKSPACE",
                workspaceId,
                null,
                context,
                () -> {
                    userWorkspaceQueryService.requireUserWorkspace(target, workspace);
                    return fileRoutingService.routeSupportWorkspace(workspace);
                });
        return ApiResponse.ok(result, context.traceId());
    }

    /** 在路由选定的目标后端签发排查专用文件 ticket。 */
    @PostMapping("/targets/{targetUserId}/workspaces/{workspaceId}/file-ws-tickets")
    public ApiResponse<WorkspaceFileSocketDtos.TicketResponse> createFileTicket(
            @PathVariable String targetUserId,
            @PathVariable String workspaceId,
            @Valid @RequestBody SupportAccessDtos.SupportFileTicketRequest request,
            @RequestHeader(name = SupportAccessApplicationService.HEADER_NAME, required = false) String grantToken,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        SupportAccessRequestContext context = requestContext(exchange);
        return ApiResponse.ok(ticketService.createSupportReadOnlyTicket(
                principal,
                grantToken,
                new UserId(targetUserId),
                new WorkspaceId(workspaceId),
                request.linuxServerId(),
                context), context.traceId());
    }

    /** 查询一年期排查审计；所有实时超级管理员可用。 */
    @GetMapping("/audit-events")
    public ApiResponse<PageResponse<SupportAccessDtos.AuditEventResponse>> listAuditEvents(
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) String targetUserId,
            @RequestParam(required = false) String incidentId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        SupportAccessRequestContext context = requestContext(exchange);
        var result = supportAccessService.listAuditEvents(
                principal,
                new SupportAccessAuditQuery(actorUserId, targetUserId, incidentId, outcome),
                RuntimeApiSupport.pageRequest(page, size),
                context);
        return ApiResponse.ok(new PageResponse<>(
                result.items().stream().map(SupportAccessDtos.AuditEventResponse::from).toList(),
                result.page(), result.size(), result.total()), context.traceId());
    }

    private SupportAccessAuthorization authorize(
            AuthPrincipal principal,
            String grantToken,
            String targetUserId,
            String action,
            String resourceType,
            String resourceId,
            SupportAccessRequestContext context) {
        return supportAccessService.authorize(
                principal,
                grantToken,
                new UserId(targetUserId),
                action,
                resourceType,
                resourceId,
                null,
                context,
                true);
    }

    private SupportAccessRequestContext requestContext(ServerWebExchange exchange) {
        String ipAddress = exchange.getRequest().getRemoteAddress() == null
                ? null
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return new SupportAccessRequestContext(
                RuntimeApiSupport.traceId(exchange),
                ipAddress,
                exchange.getRequest().getHeaders().getFirst("User-Agent"));
    }

    /**
     * 在线状态只来自公共 Java 路由快照；Redis 临时不可用时不阻断工作区清单，但文件入口保持不可选。
     */
    private PageResponse<SupportAccessDtos.WorkspaceResponse> supportWorkspacePage(
            PageResponse<com.enterprise.testagent.domain.workspace.Workspace> page,
            String traceId) {
        Map<String, BackendJavaProcess> liveBackends;
        boolean backendStateKnown = true;
        try {
            liveBackends = routeResolver.liveBackendsByServer();
        } catch (RuntimeException exception) {
            backendStateKnown = false;
            liveBackends = Map.of();
            LOGGER.warn("Support workspace backend snapshot unavailable, traceId={}", traceId, exception);
        }
        return SupportAccessDtos.workspacePage(
                page,
                liveBackends,
                routeResolver.currentLinuxServerIdValue(),
                backendStateKnown,
                pathResolver);
    }

    /**
     * 排查历史只有在权威工作区服务器的 Java 路由在线时才访问 OpenCode；未知状态按离线处理。
     */
    private boolean workspaceBackendOnline(String linuxServerId, String traceId) {
        if (linuxServerId == null) {
            return false;
        }
        if (routeResolver.isCurrent(linuxServerId)) {
            return true;
        }
        try {
            BackendJavaProcess backend = routeResolver.liveBackendsByServer().get(linuxServerId);
            return backend != null && backend.status() == BackendJavaProcessStatus.READY;
        } catch (RuntimeException exception) {
            LOGGER.warn("Support session backend snapshot unavailable, linuxServerId={}, traceId={}",
                    linuxServerId, traceId, exception);
            return false;
        }
    }

    private List<RunEventSsePayload> durableSnapshotPayloadsByRootSessionId(List<RunEventSsePayload> snapshotEvents) {
        if (eventStreamService == null || snapshotEvents == null || snapshotEvents.isEmpty()) {
            return List.of();
        }
        Set<String> rootSessionIds = new LinkedHashSet<>();
        for (RunEventSsePayload event : snapshotEvents) {
            Map<String, Object> payload = event.payload();
            Object rootSessionId = payload == null ? null : payload.get("rootSessionId");
            if (rootSessionId instanceof String value && !value.isBlank()) {
                rootSessionIds.add(value);
            }
        }
        List<RunEventSsePayload> events = new ArrayList<>();
        for (String rootSessionId : rootSessionIds) {
            List<RunEventSsePayload> durable =
                    eventStreamService.snapshotDurablePayloadsByRootSessionId(rootSessionId, 0L, 100);
            if (durable != null) {
                events.addAll(durable);
            }
        }
        return List.copyOf(events);
    }
}
