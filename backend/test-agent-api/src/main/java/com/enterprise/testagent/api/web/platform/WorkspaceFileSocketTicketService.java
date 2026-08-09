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
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessFileRoutingAffinity;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
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
        if (MODE_AGENT_CONFIG.equals(mode)) {
            String workspaceId = agentConfigWorkspaceId(request);
            rejectExperienceAgentConfig(principal.userId(), workspaceId);
            return response(ticketStore.issue(
                    workspaceId,
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
            UserOpencodeProcessFileRoutingAffinity process = userProcessAffinity(principal, traceId);
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
        UserOpencodeProcessFileRoutingAffinity process = userProcessAffinity(principal, traceId);
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

    private void requireSupportServices() {
        if (supportAccessService == null || userWorkspaceQueryService == null) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "排查只读文件服务未装配");
        }
    }

    private UserOpencodeProcessFileRoutingAffinity userProcessAffinity(AuthPrincipal principal, String traceId) {
        // 文件路由只需要用户进程的服务器归属，不触发强健康检查
        // 直接使用 fileRoutingAffinity，避免因瞬时健康检查失败导致文件树不可用
        return assignmentService.fileRoutingAffinity(principal.userId(), "opencode", traceId);
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

    /**
     * 体验区只复用普通文件与对话能力，不能借 Agent 配置文件通道触达受保护的 .opencode 目录。
     * 先走实时策略可确保历史绑定、失效资格和换目录场景同样按体验工作区失败关闭。
     */
    private void rejectExperienceAgentConfig(UserId userId, String workspaceId) {
        if (workspaceId == null) {
            return;
        }
        WorkspaceId id = new WorkspaceId(workspaceId);
        if (!ExperienceWorkspaceAccessAuthorizer.isExperienceWorkspaceId(id)) {
            return;
        }
        workspaceAccessAuthorizer.requireClassifiedFileAccess(userId, id, true);
        throw new PlatformException(ErrorCode.FORBIDDEN, "体验工作区不支持应用 Agent 配置管理");
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
