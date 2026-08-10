package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer.FileWorkspaceKind;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessFileRoutingAffinity;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import com.enterprise.testagent.workspace.WorkspaceApplicationService;
import com.enterprise.testagent.workspace.UserWorkspaceQueryService;
import com.enterprise.testagent.system.supportaccess.SupportAccessApplicationService;
import com.enterprise.testagent.system.supportaccess.SupportAccessAuthorization;
import com.enterprise.testagent.system.supportaccess.SupportAccessRequestContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 工作空间文件 WebSocket ticket 签发服务，在 HTTP 阶段完成用户、服务器与工作区校验。
 */
@Service
class WorkspaceFileSocketTicketService {

    private static final String MODE_WORKSPACE = "workspace";
    private static final String MODE_DIRECTORY_PICKER = "directory-picker";
    private static final String MODE_AGENT_CONFIG = "agent-config";
    private static final String MODE_AGENT_SKILL_HUB = "agent-skill-hub";
    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SCOPE_WORKSPACE = "WORKSPACE";

    private final WorkspaceApplicationService workspaceService;
    private final UserOpencodeProcessAssignmentService assignmentService;
    private final WorkspaceFileSocketTicketStore ticketStore;
    private final ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer;
    private final SupportAccessApplicationService supportAccessService;
    private final UserWorkspaceQueryService userWorkspaceQueryService;
    private SessionCollaborationShareService shareService;

    WorkspaceFileSocketTicketService(
            WorkspaceApplicationService workspaceService,
            UserOpencodeProcessAssignmentService assignmentService,
            WorkspaceFileSocketTicketStore ticketStore,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer) {
        this(workspaceService, assignmentService, ticketStore, workspaceAccessAuthorizer, null, null);
    }

    /** 生产构造器注入排查授权和用户工作区查询服务。 */
    @Autowired
    WorkspaceFileSocketTicketService(
            WorkspaceApplicationService workspaceService,
            UserOpencodeProcessAssignmentService assignmentService,
            WorkspaceFileSocketTicketStore ticketStore,
            ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer,
            SupportAccessApplicationService supportAccessService,
            UserWorkspaceQueryService userWorkspaceQueryService) {
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.ticketStore = Objects.requireNonNull(ticketStore, "ticketStore must not be null");
        this.workspaceAccessAuthorizer = Objects.requireNonNull(
                workspaceAccessAuthorizer,
                "workspaceAccessAuthorizer must not be null");
        this.supportAccessService = supportAccessService;
        this.userWorkspaceQueryService = userWorkspaceQueryService;
    }

    WorkspaceFileSocketDtos.TicketResponse createTicket(
            AuthPrincipal principal,
            WorkspaceFileSocketDtos.TicketRequest request,
            String traceId) {
        return createTicket(principal, request, null, traceId);
    }

    /** 分享模式只允许固定会话工作区，不开放目录选择、Agent 配置和 Hub。 */
    WorkspaceFileSocketDtos.TicketResponse createTicket(
            AuthPrincipal principal,
            WorkspaceFileSocketDtos.TicketRequest request,
            DelegatedOperationContext context,
            String traceId) {
        String mode = mode(request);
        boolean superAdmin = AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN);
        boolean appAdmin = AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN);
        String currentLinuxServerId = workspaceService.currentLinuxServerId();
        if (request.linuxServerId() != null && !request.linuxServerId().isBlank()
                && !currentLinuxServerId.equals(request.linuxServerId().trim())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "文件 WebSocket ticket 必须在目标后端签发",
                    Map.of("targetLinuxServerId", request.linuxServerId(), "currentLinuxServerId", currentLinuxServerId));
        }
        if (context != null) {
            if (!principal.userId().equals(context.actorUserId()) || !MODE_WORKSPACE.equals(mode)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "分享模式只允许当前会话的工作区文件操作");
            }
            WorkspaceId workspaceId = new WorkspaceId(requiredWorkspaceId(request));
            context.requireWorkspace(workspaceId);
            FileWorkspaceKind workspaceKind = workspaceAccessAuthorizer.requireClassifiedFileAccess(
                    context.executionOwnerUserId(), workspaceId, false);
            if (workspaceKind == FileWorkspaceKind.APP_SOURCE) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "分享模式不允许访问应用源码工作区");
            }
            UserOpencodeProcessFileRoutingAffinity process = userProcessAffinity(
                    context.executionOwnerUserId(), traceId);
            String agentLinuxServerId = process.status() == UserOpencodeProcessAvailability.READY
                    ? process.linuxServerId() : null;
            requireReadyAgentOnCurrentServer(process, currentLinuxServerId, workspaceId.value());
            workspaceService.requireWorkspaceOnCurrentServer(workspaceId, traceId);
            return response(ticketStore.issueShared(
                    workspaceId.value(), currentLinuxServerId, agentLinuxServerId,
                    context.executionOwnerUserId().value(), context.actorUserId().value(),
                    context.shareId().value(), context.sessionId().value(),
                    context.shareVersion(), context.canChat(),
                    context.expiresAt(), traceId));
        }
        if (MODE_AGENT_CONFIG.equals(mode)) {
            return response(ticketStore.issue(
                    agentConfigWorkspaceId(request),
                    currentLinuxServerId,
                    null,
                    superAdmin,
                    appAdmin,
                    principal.userId().value(),
                    mode,
                    agentConfigScope(request),
                    normalizeOptional(request.worktreeId()),
                    traceId));
        }
        if (MODE_AGENT_SKILL_HUB.equals(mode)) {
            return response(ticketStore.issue(
                    null, currentLinuxServerId, null, superAdmin, appAdmin, principal.userId().value(),
                    mode, "HUB", null, traceId));
        }
        if (MODE_WORKSPACE.equals(mode)) {
            String workspaceId = requiredWorkspaceId(request);
            FileWorkspaceKind workspaceKind = workspaceAccessAuthorizer.requireClassifiedFileAccess(
                    principal.userId(),
                    new WorkspaceId(workspaceId),
                    false);
            UserOpencodeProcessFileRoutingAffinity process = userProcessAffinity(principal.userId(), traceId);
            String agentLinuxServerId = process.status() == UserOpencodeProcessAvailability.READY
                    ? process.linuxServerId()
                    : null;
            requireReadyAgentOnCurrentServer(process, currentLinuxServerId, workspaceId);
            workspaceService.requireWorkspaceOnCurrentServer(new WorkspaceId(workspaceId), traceId);
            return response(ticketStore.issue(
                    workspaceId,
                    currentLinuxServerId,
                    agentLinuxServerId,
                    workspaceKind == FileWorkspaceKind.APP_SOURCE,
                    superAdmin,
                    appAdmin,
                    principal.userId().value(),
                    mode,
                    null,
                    null,
                    traceId));
        }
        if (!MODE_DIRECTORY_PICKER.equals(mode)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "文件 WebSocket ticket 模式无效", Map.of("mode", mode));
        }
        UserOpencodeProcessFileRoutingAffinity process = userProcessAffinity(principal.userId(), traceId);
        String agentLinuxServerId = process.status() == UserOpencodeProcessAvailability.READY ? process.linuxServerId() : null;
        if (!superAdmin) {
            requireReadyAgentOnCurrentServer(process, currentLinuxServerId, "directory-picker");
        }
        return response(ticketStore.issue(null, currentLinuxServerId, agentLinuxServerId, superAdmin, mode, null, null, traceId));
    }

    WorkspaceFileSocketTicket consume(String ticket, String origin) {
        return ticketStore.consume(ticket, origin);
    }

    /** 在目标 Java 上签发排查专用只读文件 ticket。 */
    WorkspaceFileSocketDtos.TicketResponse createSupportReadOnlyTicket(
            AuthPrincipal principal,
            String rawGrantToken,
            UserId targetUserId,
            WorkspaceId workspaceId,
            String requestedLinuxServerId,
            SupportAccessRequestContext requestContext) {
        requireSupportServices();
        SupportAccessAuthorization authorization = supportAccessService.authorize(
                principal, rawGrantToken, targetUserId, "FILE_TICKET_ISSUED", "WORKSPACE",
                workspaceId.value(), null, requestContext, true);
        userWorkspaceQueryService.requireUserWorkspace(targetUserId, workspaceId);
        String currentLinuxServerId = workspaceService.currentLinuxServerId();
        if (requestedLinuxServerId == null || !currentLinuxServerId.equals(requestedLinuxServerId.trim())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "排查文件 ticket 必须在目标后端签发",
                    Map.of("currentLinuxServerId", currentLinuxServerId));
        }
        workspaceService.requireWorkspaceOnCurrentServer(workspaceId, requestContext.traceId());
        WorkspaceFileSocketTicket ticket = ticketStore.issueSupportReadOnly(
                workspaceId.value(), currentLinuxServerId, authorization.actor().userId().value(),
                targetUserId.value(), authorization.grant().grantId(), authorization.session().grantTokenDigest(),
                authorization.session().sessionDigest(), requestContext.traceId());
        supportAccessService.recordReadOutcome(
                authorization, "FILE_TICKET_ISSUED", "WORKSPACE", workspaceId.value(), null,
                "SUCCESS", null, requestContext);
        return response(ticket);
    }

    /**
     * 每条 workspace RPC 重新校验 ticket、当前 JVM、用户 agent、Workspace 与托管副本事实。
     *
     * <p>连接建立后的 binding 迁移不能继续沿用旧 socket；这里只复用公共 assignment 与 workspace
     * 校验程序，不扫描 Redis、不自行选择路由，也不做本机降级。
     */
    SupportAccessAuthorization authorizeWorkspaceRpc(WorkspaceFileSocketTicket ticket, WorkspaceId workspaceId) {
        if (ticket == null
                || !MODE_WORKSPACE.equals(ticket.mode())
                || ticket.userId() == null
                || ticket.userId().isBlank()
                || ticket.workspaceId() == null
                || !ticket.workspaceId().equals(workspaceId.value())) {
            throw workspaceRpcDenied();
        }
        if (ticket.supportReadOnly()) {
            requireSupportServices();
            if (ticket.supportTargetUserId() == null
                    || ticket.supportGrantId() == null
                    || ticket.supportGrantTokenDigest() == null
                    || ticket.supportActorSessionDigest() == null) {
                throw workspaceRpcDenied();
            }
            String currentLinuxServerId = workspaceService.currentLinuxServerId();
            if (!Objects.equals(currentLinuxServerId, ticket.linuxServerId())) {
                throw workspaceRpcDenied();
            }
            UserId targetUserId = new UserId(ticket.supportTargetUserId());
            userWorkspaceQueryService.requireUserWorkspace(targetUserId, workspaceId);
            workspaceService.requireWorkspaceOnCurrentServer(workspaceId, ticket.traceId());
            SupportAccessAuthorization authorization = supportAccessService.authorizeByDigest(
                    ticket.userId(), ticket.supportActorSessionDigest(), ticket.supportGrantTokenDigest(), targetUserId);
            if (!ticket.supportGrantId().equals(authorization.grant().grantId())) {
                throw workspaceRpcDenied();
            }
            return authorization;
        }
        if (ticket.sharedSession()) {
            if (shareService == null || ticket.shareActorUserId() == null
                    || ticket.executionOwnerUserId() == null || ticket.shareVersion() == null
                    || ticket.shareSessionId() == null) {
                throw workspaceRpcDenied();
            }
            DelegatedOperationContext refreshed = shareService.refreshAccess(
                    new UserId(ticket.shareActorUserId()),
                    new com.enterprise.testagent.domain.sessionshare.SessionShareId(ticket.shareId()),
                    ticket.traceId());
            refreshed.requireWorkspace(workspaceId);
            if (refreshed.shareVersion() != ticket.shareVersion()
                    || !refreshed.sessionId().value().equals(ticket.shareSessionId())
                    || !refreshed.executionOwnerUserId().value().equals(ticket.executionOwnerUserId())) {
                throw workspaceRpcDenied();
            }
        }
        String currentLinuxServerId = workspaceService.currentLinuxServerId();
        if (!Objects.equals(currentLinuxServerId, ticket.linuxServerId())
                || !Objects.equals(currentLinuxServerId, ticket.agentLinuxServerId())) {
            throw workspaceRpcDenied();
        }
        UserId userId = new UserId(ticket.userId());
        UserOpencodeProcessFileRoutingAffinity currentAffinity =
                assignmentService.fileRoutingAffinity(userId, "opencode", ticket.traceId());
        if (currentAffinity.status() != UserOpencodeProcessAvailability.READY
                || !Objects.equals(currentLinuxServerId, currentAffinity.linuxServerId())) {
            throw workspaceRpcDenied();
        }
        FileWorkspaceKind currentKind = workspaceAccessAuthorizer.requireClassifiedFileAccess(
                userId, workspaceId, false);
        if (ticket.appSourceWorkspace() && currentKind != FileWorkspaceKind.APP_SOURCE) {
            throw workspaceRpcDenied();
        }
        workspaceService.requireWorkspaceOnCurrentServer(workspaceId, ticket.traceId());
        return null;
    }

    /** 记录文件 RPC 审计结果。 */
    void recordSupportRpc(
            SupportAccessAuthorization authorization,
            String operation,
            WorkspaceId workspaceId,
            String path,
            String outcome,
            String errorCode,
            String traceId) {
        requireSupportServices();
        supportAccessService.recordReadOutcome(
                authorization,
                operation.toUpperCase(java.util.Locale.ROOT).replace('.', '_'),
                "WORKSPACE_FILE",
                workspaceId.value(),
                path,
                outcome,
                errorCode,
                new SupportAccessRequestContext(traceId, null, null));
    }

    /** 分享文件操作审计仅保存路径 SHA-256，不保存文件正文、明文路径或上传内容。 */
    void recordSharedRpc(
            WorkspaceFileSocketTicket ticket,
            String operation,
            WorkspaceId workspaceId,
            String path,
            String outcome,
            String errorCode,
            String traceId) {
        if (shareService == null || ticket == null || !ticket.sharedSession()
                || ticket.shareSessionId() == null || ticket.shareActorUserId() == null
                || ticket.executionOwnerUserId() == null) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "分享文件审计上下文不完整");
        }
        String action = ("WORKSPACE_FILE_" + operation).toUpperCase(java.util.Locale.ROOT)
                .replace('.', '_');
        shareService.recordOperationSnapshot(
                new com.enterprise.testagent.domain.sessionshare.SessionShareId(ticket.shareId()),
                new com.enterprise.testagent.domain.session.SessionId(ticket.shareSessionId()),
                workspaceId,
                new UserId(ticket.shareActorUserId()),
                new UserId(ticket.executionOwnerUserId()),
                action.length() <= 64 ? action : action.substring(0, 64),
                "WORKSPACE_FILE",
                workspaceId.value(),
                path,
                outcome,
                errorCode,
                traceId);
    }

    private void requireSupportServices() {
        if (supportAccessService == null || userWorkspaceQueryService == null) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "排查只读文件服务未装配");
        }
    }

    private UserOpencodeProcessFileRoutingAffinity userProcessAffinity(UserId userId, String traceId) {
        // 文件路由只需要用户进程的服务器归属，不触发强健康检查
        // 直接使用 fileRoutingAffinity，避免因瞬时健康检查失败导致文件树不可用
        return assignmentService.fileRoutingAffinity(userId, "opencode", traceId);
    }

    /** 可选注入保持既有轻量测试装配；生产环境始终用于每条分享 RPC 重新鉴权。 */
    @Autowired(required = false)
    void configureSessionShareService(SessionCollaborationShareService shareService) {
        this.shareService = shareService;
    }

    private void requireReadyAgentOnCurrentServer(
            UserOpencodeProcessFileRoutingAffinity process,
            String currentLinuxServerId,
            String workspaceId) {
        if (process.status() != UserOpencodeProcessAvailability.READY || process.linuxServerId() == null) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "当前用户 TestAgent 进程不可用",
                    Map.of("workspaceId", workspaceId, "status", process.status().name()));
        }
        if (!currentLinuxServerId.equals(process.linuxServerId())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "工作空间与 agent 不在同一服务器",
                    Map.of(
                            "workspaceId", workspaceId,
                            "agentLinuxServerId", process.linuxServerId(),
                            "currentLinuxServerId", currentLinuxServerId));
        }
    }

    private PlatformException workspaceRpcDenied() {
        return new PlatformException(ErrorCode.FORBIDDEN, "工作区文件 ticket 运行路由已失效");
    }

    private WorkspaceFileSocketDtos.TicketResponse response(WorkspaceFileSocketTicket ticket) {
        return new WorkspaceFileSocketDtos.TicketResponse(
                ticket.ticket(),
                ticket.expiresAt(),
                WorkspaceFileRoutingService.WEB_SOCKET_PATH + "?ticket=" + ticket.ticket());
    }

    private String mode(WorkspaceFileSocketDtos.TicketRequest request) {
        if (request.mode() != null && !request.mode().isBlank()) {
            return request.mode().trim();
        }
        return request.workspaceId() == null || request.workspaceId().isBlank() ? MODE_DIRECTORY_PICKER : MODE_WORKSPACE;
    }

    private String requiredWorkspaceId(WorkspaceFileSocketDtos.TicketRequest request) {
        if (request.workspaceId() == null || request.workspaceId().isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "workspaceId 不能为空");
        }
        return request.workspaceId().trim();
    }

    private String agentConfigWorkspaceId(WorkspaceFileSocketDtos.TicketRequest request) {
        return SCOPE_WORKSPACE.equals(agentConfigScope(request)) ? requiredWorkspaceId(request) : null;
    }

    private String agentConfigScope(WorkspaceFileSocketDtos.TicketRequest request) {
        String scope = request.scope() == null ? "" : request.scope().trim().toUpperCase(java.util.Locale.ROOT);
        if (SCOPE_PUBLIC.equals(scope) || SCOPE_WORKSPACE.equals(scope)) {
            return scope;
        }
        throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Agent 配置文件 scope 无效", Map.of("scope", request.scope()));
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
