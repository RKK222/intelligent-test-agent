package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.enterprise.testagent.system.management.team.SystemAdminTeamApplicationService;
import com.enterprise.testagent.system.management.team.SystemAdminTeamResponses.TeamMutationResponse;
import com.enterprise.testagent.system.management.team.TeamOversightRequestContext;
import com.enterprise.testagent.workspace.TeamWorkspaceExportService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 系统管理员团队人员名单入口；团队代码和文件入口位于 workspace-management。 */
@RestController
@RequestMapping("/api/internal/platform/system-management")
public class SystemAdminTeamController {

    private final SystemAdminTeamApplicationService service;
    private final TeamWorkspaceExportService exportService;

    public SystemAdminTeamController(
            SystemAdminTeamApplicationService service,
            TeamWorkspaceExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    /** 超级管理员分页选择实际系统管理员。 */
    @GetMapping("/system-admins")
    public ApiResponse<Object> listSystemAdmins(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SUPER_ADMIN);
        return ok(exchange, service.listSystemAdmins(
                principal, keyword, RuntimeApiSupport.pageRequest(page, size), context(exchange)));
    }

    @GetMapping("/team-members")
    public ApiResponse<Object> listMembers(
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SYSTEM_ADMIN);
        return ok(exchange, service.listMembers(
                principal, scopeMode, ownerUserId, keyword,
                RuntimeApiSupport.pageRequest(page, size), context(exchange)));
    }

    @GetMapping("/team-member-candidates")
    public ApiResponse<Object> listCandidates(
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SYSTEM_ADMIN);
        return ok(exchange, service.listCandidates(
                principal, scopeMode, ownerUserId, keyword,
                RuntimeApiSupport.pageRequest(page, size), context(exchange)));
    }

    @PostMapping("/team-members")
    public ApiResponse<Object> addMember(
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            @RequestBody SystemAdminTeamDtos.AddMemberRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SYSTEM_ADMIN);
        return ok(exchange, service.addMember(
                principal, scopeMode, ownerUserId, request.memberUserId(), context(exchange)));
    }

    @DeleteMapping("/team-members/{memberUserId}")
    public ApiResponse<Object> removeMember(
            @PathVariable String memberUserId,
            @RequestParam(required = false) TeamScopeMode scopeMode,
            @RequestParam(required = false) String ownerUserId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_SYSTEM_ADMIN);
        TeamMutationResponse result = service.removeMember(
                principal, scopeMode, ownerUserId, memberUserId, context(exchange));
        exportService.invalidateTeamMember(result.ownerUserId(), result.memberUserId());
        return ok(exchange, result);
    }

    private TeamOversightRequestContext context(ServerWebExchange exchange) {
        String ipAddress = exchange.getRequest().getRemoteAddress() == null
                ? null
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return new TeamOversightRequestContext(
                RuntimeApiSupport.traceId(exchange), ipAddress,
                exchange.getRequest().getHeaders().getFirst("User-Agent"));
    }

    private ApiResponse<Object> ok(ServerWebExchange exchange, Object data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }
}
