package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessFileRoutingAffinity;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessStatusResponse;
import com.enterprise.testagent.opencode.runtime.process.WorkspaceFileRoutingService;
import com.enterprise.testagent.workspace.WorkspaceApplicationService;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 工作空间文件 WebSocket ticket 签发服务，在 HTTP 阶段完成用户、服务器与工作区校验。
 */
@Service
class WorkspaceFileSocketTicketService {

    private static final String MODE_WORKSPACE = "workspace";
    private static final String MODE_DIRECTORY_PICKER = "directory-picker";
    private static final String MODE_AGENT_CONFIG = "agent-config";
    private static final String SCOPE_PUBLIC = "PUBLIC";
    private static final String SCOPE_WORKSPACE = "WORKSPACE";

    private final WorkspaceApplicationService workspaceService;
    private final UserOpencodeProcessAssignmentService assignmentService;
    private final WorkspaceFileSocketTicketStore ticketStore;

    WorkspaceFileSocketTicketService(
            WorkspaceApplicationService workspaceService,
            UserOpencodeProcessAssignmentService assignmentService,
            WorkspaceFileSocketTicketStore ticketStore) {
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService must not be null");
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.ticketStore = Objects.requireNonNull(ticketStore, "ticketStore must not be null");
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
        UserOpencodeProcessFileRoutingAffinity process = userProcessAffinity(principal, traceId);
        String agentLinuxServerId = process.status() == UserOpencodeProcessAvailability.READY ? process.linuxServerId() : null;
        if (MODE_WORKSPACE.equals(mode)) {
            String workspaceId = requiredWorkspaceId(request);
            requireReadyAgentOnCurrentServer(process, currentLinuxServerId, workspaceId);
            workspaceService.requireWorkspaceOnCurrentServer(new WorkspaceId(workspaceId), traceId);
            return response(ticketStore.issue(workspaceId, currentLinuxServerId, agentLinuxServerId, superAdmin, appAdmin,
                    principal.userId().value(), mode, null, null, traceId));
        }
        if (!MODE_DIRECTORY_PICKER.equals(mode)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "文件 WebSocket ticket 模式无效", Map.of("mode", mode));
        }
        if (!superAdmin) {
            requireReadyAgentOnCurrentServer(process, currentLinuxServerId, "directory-picker");
        }
        return response(ticketStore.issue(null, currentLinuxServerId, agentLinuxServerId, superAdmin, mode, null, null, traceId));
    }

    WorkspaceFileSocketTicket consume(String ticket, String origin) {
        return ticketStore.consume(ticket, origin);
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
