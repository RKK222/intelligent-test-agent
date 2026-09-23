package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer.FileWorkspaceKind;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceAccessAuthorizer;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessFileRoutingAffinity;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import com.enterprise.testagent.workspace.WorkspaceApplicationService;
import com.enterprise.testagent.workspace.UserWorkspaceQueryService;
import com.enterprise.testagent.system.supportaccess.SupportAccessApplicationService;
import com.enterprise.testagent.system.supportaccess.SupportAccessAuthorization;
import com.enterprise.testagent.system.supportaccess.SupportAccessRequestContext;
import com.enterprise.testagent.system.management.team.SystemAdminTeamApplicationService;
import com.enterprise.testagent.system.management.team.SystemAdminTeamApplicationService.AuthorizedTarget;
import com.enterprise.testagent.system.management.team.TeamOversightRequestContext;
import com.enterprise.testagent.workspace.TeamWorkspaceApplicationService;
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
    private LocalClientWorkspaceRepository localWorkspaceRepository;
    private LocalClientConnectionStore localConnectionStore;
    private LocalClientInstanceRepository localInstanceRepository;
    private BackendJavaRouteResolver backendRouteResolver;
    private SystemAdminTeamApplicationService systemAdminTeams;
    private TeamWorkspaceApplicationService teamWorkspaces;

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
        LocalClientWorkspaceBinding localBinding = MODE_WORKSPACE.equals(mode)
                ? localBinding(request)
                : null;
        if (request.linuxServerId() != null && !request.linuxServerId().isBlank()
                && !currentLinuxServerId.equals(request.linuxServerId().trim())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "文件 WebSocket ticket 必须在目标后端签发",
                    Map.of("targetLinuxServerId", request.linuxServerId(), "currentLinuxServerId", currentLinuxServerId));
        }
        if (context != null) {
            if (localBinding != null) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "本地工作区首版不开放协作分享");
            }
            if (!principal.userId().equals(context.actorUserId()) || !MODE_WORKSPACE.equals(mode)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "分享模式只允许当前会话的工作区文件操作");
            }
            WorkspaceId workspaceId = new WorkspaceId(requiredWorkspaceId(request));
            context.requireWorkspace(workspaceId);
            FileWorkspaceKind workspaceKind = workspaceAccessAuthorizer.requireClassifiedFileAccess(
                    context.executionOwnerUserId(), workspaceId, false);
            if (readOnlyWorkspaceKind(workspaceKind)) {
                throw new PlatformException(ErrorCode.FORBIDDEN, "分享模式不允许访问只读引用工作区");
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
            LocalPersonalReference localPersonal = localPersonalReference(request.worktreeId());
            if (localPersonal != null) {
                if (!SCOPE_PUBLIC.equals(request.scope())) {
                    throw new PlatformException(ErrorCode.FORBIDDEN, "本地个人公共能力 ticket 只允许 PUBLIC scope");
                }
                LocalClientConnectionRoute route = requireCurrentLocalRoute(
                        new LocalClientInstanceId(localPersonal.clientInstanceId()),
                        principal.userId(), localPersonal.connectionGeneration());
                if (localInstanceRepository != null) {
                    var instance = localInstanceRepository.findById(new LocalClientInstanceId(localPersonal.clientInstanceId()))
                            .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "本地客户端实例不存在"));
                    if (!instance.selfUpdateCapabilities().contains("PUBLIC_CAPABILITY_PERSONAL_EDIT_V1")) {
                        throw new PlatformException(ErrorCode.FORBIDDEN, "当前本地客户端不支持公共能力个人编辑");
                    }
                }
                return response(ticketStore.issueLocalAgentConfig(
                        principal.userId().value(), localPersonal.clientInstanceId(),
                        route.connectionGeneration(), appAdmin, traceId));
            }
            String workspaceId = agentConfigWorkspaceId(request);
            rejectExperienceAgentConfig(principal.userId(), workspaceId);
            return response(ticketStore.issue(
                    workspaceId,
                    currentLinuxServerId,
                    null,
                    false,
                    superAdmin,
                    appAdmin,
                    principal.userId().value(),
                    principal.unifiedAuthId(),
                    mode,
                    agentConfigScope(request),
                    normalizeOptional(request.worktreeId()),
                    traceId));
        }
        if (MODE_AGENT_SKILL_HUB.equals(mode)) {
            return response(ticketStore.issue(
                    null, currentLinuxServerId, null, false, superAdmin, appAdmin, principal.userId().value(),
                    principal.unifiedAuthId(), mode, "HUB", null, traceId));
        }
        if (MODE_WORKSPACE.equals(mode)) {
            String workspaceId = requiredWorkspaceId(request);
            FileWorkspaceKind workspaceKind = workspaceAccessAuthorizer.requireClassifiedFileAccess(
                    principal.userId(),
                    new WorkspaceId(workspaceId),
                    false);
            if (localBinding != null) {
                if (!localBinding.userId().equals(principal.userId())) {
                    throw new PlatformException(ErrorCode.FORBIDDEN, "无权访问其他用户的本地工作区");
                }
                LocalClientConnectionRoute route = requireCurrentLocalRoute(
                        localBinding.clientInstanceId(), principal.userId(), request.connectionGeneration());
                return response(ticketStore.issueLocal(
                        workspaceId,
                        principal.userId().value(),
                        mode,
                        localBinding.clientInstanceId().value(),
                        route.connectionGeneration(),
                        localBinding.rootDigest(),
                        appAdmin,
                        traceId));
            }
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
                    readOnlyWorkspaceKind(workspaceKind),
                    superAdmin,
                    appAdmin,
                    principal.userId().value(),
                    principal.unifiedAuthId(),
                    mode,
                    null,
                    null,
                    traceId));
        }
        if (!MODE_DIRECTORY_PICKER.equals(mode)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "文件 WebSocket ticket 模式无效", Map.of("mode", mode));
        }
        if (request.localClientInstanceId() != null && !request.localClientInstanceId().isBlank()) {
            LocalClientInstanceId clientInstanceId = new LocalClientInstanceId(request.localClientInstanceId().trim());
            LocalClientConnectionRoute route = requireCurrentLocalRoute(
                    clientInstanceId, principal.userId(), request.connectionGeneration());
            return response(ticketStore.issueLocal(
                    null,
                    principal.userId().value(),
                    mode,
                    clientInstanceId.value(),
                    route.connectionGeneration(),
                    null,
                    appAdmin,
                    traceId));
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

    /** upgrade 前只检查 ticket 是否有效，实际升级时再由 consume 原子消费。 */
    void validate(String ticket, String origin) {
        ticketStore.validate(ticket, origin);
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

    /** 在个人 worktree 权威节点签发团队只读 ticket。 */
    WorkspaceFileSocketDtos.TicketResponse createTeamReadOnlyTicket(
            AuthPrincipal principal,
            TeamScopeMode mode,
            String ownerUserId,
            String personalWorkspaceId,
            String requestedLinuxServerId,
            TeamOversightRequestContext requestContext) {
        requireTeamServices();
        var scope = systemAdminTeams.authorizeScope(principal, mode, ownerUserId);
        UserId owner = scope.scope().global() ? null : new UserId(scope.scope().ownerUserId());
        var personal = teamWorkspaces.personalWorkspace(
                scope.scope().global(), owner, personalWorkspaceId);
        AuthorizedTarget target = systemAdminTeams.authorizeTarget(
                scope.actor().user().userId(), scope.scope().mode(), scope.scope().ownerUserId(),
                personal.workspace().userId());
        String currentLinuxServerId = workspaceService.currentLinuxServerId();
        if (requestedLinuxServerId == null
                || !currentLinuxServerId.equals(requestedLinuxServerId.trim())
                || !currentLinuxServerId.equals(personal.linuxServerId())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "团队文件 ticket 必须在个人工作区权威后端签发",
                    Map.of("currentLinuxServerId", currentLinuxServerId));
        }
        WorkspaceId workspaceId = personal.workspace().runtimeWorkspaceId();
        workspaceService.requireWorkspaceOnCurrentServer(workspaceId, requestContext.traceId());
        WorkspaceFileSocketTicket ticket = ticketStore.issueTeamReadOnly(
                workspaceId.value(), currentLinuxServerId, scope.actor().user().userId().value(),
                target.target().userId().value(), scope.scope().mode().name(), scope.scope().ownerUserId(),
                personalWorkspaceId, requestContext.traceId());
        systemAdminTeams.recordOutcome(
                scope.actor(), target.target(), "FILE_TICKET_ISSUED", "PERSONAL_WORKSPACE",
                personalWorkspaceId, null, "SUCCESS", null, requestContext);
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
        if (ticket.localClient()) {
            return authorizeLocalWorkspaceRpc(ticket, workspaceId);
        }
        if (ticket.supportReadOnly()) {
            if (ticket.teamReadOnly()) {
                authorizeTeamWorkspaceRpc(ticket, workspaceId);
                return null;
            }
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
        if (ticket.appSourceWorkspace() != readOnlyWorkspaceKind(currentKind)) {
            throw workspaceRpcDenied();
        }
        workspaceService.requireWorkspaceOnCurrentServer(workspaceId, ticket.traceId());
        return null;
    }

    /** 团队文件 RPC 每次重新校验实时角色、团队关系、人员和 worktree 映射。 */
    AuthorizedTarget authorizeTeamWorkspaceRpc(WorkspaceFileSocketTicket ticket, WorkspaceId workspaceId) {
        requireTeamServices();
        if (ticket == null || !ticket.teamReadOnly() || ticket.userId() == null
                || ticket.supportTargetUserId() == null || ticket.supportActorSessionDigest() == null
                || ticket.workspaceId() == null || !ticket.workspaceId().equals(workspaceId.value())
                || !Objects.equals(workspaceService.currentLinuxServerId(), ticket.linuxServerId())) {
            throw workspaceRpcDenied();
        }
        TeamScopeMode mode;
        try {
            mode = TeamScopeMode.valueOf(ticket.supportGrantId().substring("TEAM:".length()));
        } catch (RuntimeException exception) {
            throw workspaceRpcDenied();
        }
        String ownerUserId = ticket.supportGrantTokenDigest();
        AuthorizedTarget target = systemAdminTeams.authorizeTarget(
                new UserId(ticket.userId()), mode, ownerUserId,
                new UserId(ticket.supportTargetUserId()));
        UserId owner = mode == TeamScopeMode.GLOBAL ? null
                : (mode == TeamScopeMode.MY_TEAM ? new UserId(ticket.userId()) : new UserId(ownerUserId));
        var personal = teamWorkspaces.personalWorkspace(
                mode == TeamScopeMode.GLOBAL, owner, ticket.supportActorSessionDigest());
        if (!personal.workspace().runtimeWorkspaceId().equals(workspaceId)
                || !personal.workspace().userId().equals(target.target().userId())
                || !Objects.equals(personal.linuxServerId(), ticket.linuxServerId())) {
            throw workspaceRpcDenied();
        }
        workspaceService.requireWorkspaceOnCurrentServer(workspaceId, ticket.traceId());
        return target;
    }

    /** 团队文件访问审计只记录路径摘要，由领域审计仓储统一落库。 */
    void recordTeamRpc(
            WorkspaceFileSocketTicket ticket,
            AuthorizedTarget target,
            String operation,
            WorkspaceId workspaceId,
            String path,
            String outcome,
            String errorCode,
            String traceId) {
        systemAdminTeams.recordOutcome(
                target.actor(), target.target(),
                operation.toUpperCase(java.util.Locale.ROOT).replace('.', '_'),
                "WORKSPACE_FILE", ticket.supportActorSessionDigest(), path,
                outcome, errorCode, new TeamOversightRequestContext(traceId, null, null));
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

    private void requireTeamServices() {
        if (systemAdminTeams == null || teamWorkspaces == null) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "团队只读文件服务未装配");
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

    /** 可选 setter 保持现有轻量测试构造器兼容；生产环境由 Spring 完整装配。 */
    @Autowired(required = false)
    void configureTeamWorkspaceServices(
            SystemAdminTeamApplicationService systemAdminTeams,
            TeamWorkspaceApplicationService teamWorkspaces) {
        this.systemAdminTeams = Objects.requireNonNull(systemAdminTeams);
        this.teamWorkspaces = Objects.requireNonNull(teamWorkspaces);
    }

    /** 生产环境装配本地客户端精确连接路由；保留既有轻量单元测试构造路径。 */
    @Autowired(required = false)
    void configureLocalClientServices(
            LocalClientWorkspaceRepository localWorkspaceRepository,
            LocalClientConnectionStore localConnectionStore,
            BackendJavaRouteResolver backendRouteResolver,
            LocalClientInstanceRepository localInstanceRepository) {
        this.localWorkspaceRepository = Objects.requireNonNull(
                localWorkspaceRepository, "localWorkspaceRepository must not be null");
        this.localConnectionStore = Objects.requireNonNull(
                localConnectionStore, "localConnectionStore must not be null");
        this.backendRouteResolver = Objects.requireNonNull(
                backendRouteResolver, "backendRouteResolver must not be null");
        this.localInstanceRepository = Objects.requireNonNull(
                localInstanceRepository, "localInstanceRepository must not be null");
    }

    private SupportAccessAuthorization authorizeLocalWorkspaceRpc(
            WorkspaceFileSocketTicket ticket,
            WorkspaceId workspaceId) {
        if (ticket.supportReadOnly() || ticket.sharedSession()
                || localWorkspaceRepository == null || localConnectionStore == null
                || backendRouteResolver == null || ticket.userId() == null) {
            throw workspaceRpcDenied();
        }
        UserId userId = new UserId(ticket.userId());
        LocalClientWorkspaceBinding binding = localWorkspaceRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(this::workspaceRpcDenied);
        if (!binding.userId().equals(userId)
                || !binding.clientInstanceId().value().equals(ticket.localClientInstanceId())
                || !Objects.equals(binding.rootDigest(), ticket.rootDigest())) {
            throw workspaceRpcDenied();
        }
        LocalClientConnectionRoute route = localConnectionStore.find(binding.clientInstanceId())
                .orElseThrow(this::workspaceRpcDenied);
        if (!route.userId().equals(userId)
                || route.connectionGeneration() != ticket.connectionGeneration()
                || !backendRouteResolver.isCurrent(route.backendProcessId())) {
            throw workspaceRpcDenied();
        }
        workspaceAccessAuthorizer.requireClassifiedFileAccess(userId, workspaceId, false);
        return null;
    }

    /** 每条本地个人公共能力 RPC 都重新核对 user、客户端代次和 capability，拒绝迟到旧连接。 */
    void authorizeLocalAgentConfigRpc(WorkspaceFileSocketTicket ticket) {
        if (!ticket.localClient() || !MODE_AGENT_CONFIG.equals(ticket.mode())
                || !SCOPE_PUBLIC.equals(ticket.scope())
                || ticket.userId() == null || ticket.localClientInstanceId() == null
                || localConnectionStore == null || backendRouteResolver == null || localInstanceRepository == null) {
            throw workspaceRpcDenied();
        }
        LocalClientInstanceId clientId = new LocalClientInstanceId(ticket.localClientInstanceId());
        LocalClientConnectionRoute route = localConnectionStore.find(clientId).orElseThrow(this::workspaceRpcDenied);
        if (!route.userId().value().equals(ticket.userId())
                || route.connectionGeneration() != ticket.connectionGeneration()
                || !backendRouteResolver.isCurrent(route.backendProcessId())) {
            throw workspaceRpcDenied();
        }
        var instance = localInstanceRepository.findById(clientId).orElseThrow(this::workspaceRpcDenied);
        if (!instance.selfUpdateCapabilities().contains("PUBLIC_CAPABILITY_PERSONAL_EDIT_V1")) {
            throw workspaceRpcDenied();
        }
    }

    private LocalClientWorkspaceBinding localBinding(WorkspaceFileSocketDtos.TicketRequest request) {
        if (localWorkspaceRepository == null) {
            return null;
        }
        return localWorkspaceRepository.findByWorkspaceId(new WorkspaceId(requiredWorkspaceId(request)))
                .orElse(null);
    }

    private LocalClientConnectionRoute requireCurrentLocalRoute(
            LocalClientInstanceId clientInstanceId,
            UserId userId,
            Long requestedGeneration) {
        if (localConnectionStore == null || backendRouteResolver == null) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "本地客户端路由服务未装配");
        }
        LocalClientConnectionRoute route = localConnectionStore.find(clientInstanceId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "本地客户端离线",
                        Map.of("clientInstanceId", clientInstanceId.value())));
        if (!route.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端不属于当前用户");
        }
        if (requestedGeneration != null && requestedGeneration.longValue() != route.connectionGeneration()) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端连接已换代，请重新路由");
        }
        if (!backendRouteResolver.isCurrent(route.backendProcessId())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "本地文件 ticket 必须在连接持有 Java 签发",
                    Map.of("backendProcessId", route.backendProcessId().value()));
        }
        return route;
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

    private LocalPersonalReference localPersonalReference(String worktreeId) {
        if (worktreeId == null || !worktreeId.startsWith("LOCAL_CLIENT_PERSONAL:")) return null;
        String[] parts = worktreeId.split(":", -1);
        if (parts.length != 3 || parts[1].isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "本地客户端个人配置路由无效");
        }
        try {
            long generation = Long.parseLong(parts[2]);
            if (generation < 1) throw new NumberFormatException();
            return new LocalPersonalReference(parts[1], generation);
        } catch (NumberFormatException exception) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "本地客户端连接代次无效");
        }
    }

    private record LocalPersonalReference(String clientInstanceId, long connectionGeneration) {
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

    /** 应用源码副本和历史自动化运行目录共用文件通道的严格只读边界。 */
    private boolean readOnlyWorkspaceKind(FileWorkspaceKind kind) {
        return kind == FileWorkspaceKind.APP_SOURCE || kind == FileWorkspaceKind.AUTOMATION_REFERENCE;
    }
}
