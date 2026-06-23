package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.workspace.ManagedWorkspaceApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * 应用版本工作区 HTTP 入口，负责认证主体、traceId 和请求参数转换。
 */
@RestController
@RequestMapping("/api/internal/platform/workspace-management")
public class ManagedWorkspaceController {

    private final ManagedWorkspaceApplicationService service;

    public ManagedWorkspaceController(ManagedWorkspaceApplicationService service) {
        this.service = service;
    }

    @GetMapping("/applications")
    public ApiResponse<Object> listApplications(ServerWebExchange exchange) {
        return ok(exchange, service.listApplications(userId(exchange)));
    }

    @GetMapping("/applications/{appId}/workspace-templates")
    public ApiResponse<Object> listTemplates(@PathVariable String appId, ServerWebExchange exchange) {
        return ok(exchange, service.listTemplates(appId, userId(exchange)));
    }

    @GetMapping("/applications/{appId}/workspace-templates/{templateId}/versions")
    public ApiResponse<Object> listVersions(
            @PathVariable String templateId,
            ServerWebExchange exchange) {
        return ok(exchange, service.listVersions(templateId, userId(exchange)));
    }

    @PostMapping("/applications/{appId}/workspace-templates/{templateId}/versions")
    public ApiResponse<Object> createVersion(
            @PathVariable String appId,
            @PathVariable String templateId,
            @RequestBody ManagedWorkspaceDtos.CreateVersionRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.createVersion(
                appId,
                templateId,
                request.version(),
                request.branch(),
                userId(exchange),
                RuntimeApiSupport.traceId(exchange)));
    }

    @GetMapping("/workspace-versions/{versionId}/personal-workspaces")
    public ApiResponse<Object> listPersonalWorkspaces(@PathVariable String versionId, ServerWebExchange exchange) {
        return ok(exchange, service.listPersonalWorkspaces(versionId, userId(exchange)));
    }

    @PostMapping("/workspace-versions/{versionId}/personal-workspaces")
    public ApiResponse<Object> createPersonalWorkspace(
            @PathVariable String versionId,
            @RequestBody ManagedWorkspaceDtos.CreatePersonalWorkspaceRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.createPersonalWorkspace(
                versionId,
                request.workspaceName(),
                userId(exchange),
                RuntimeApiSupport.traceId(exchange)));
    }

    @GetMapping("/recent-workspace")
    public ApiResponse<Object> recentWorkspace(ServerWebExchange exchange) {
        return ok(exchange, service.recentWorkspace(userId(exchange)).orElse(null));
    }

    @GetMapping("/applications/{appId}/recent-workspace")
    public ApiResponse<Object> recentWorkspaceByApplication(@PathVariable String appId, ServerWebExchange exchange) {
        return ok(exchange, service.recentWorkspace(appId, userId(exchange)).orElse(null));
    }

    @PostMapping("/workspaces/{workspaceId}/recent")
    public ApiResponse<Object> markRecentWorkspace(@PathVariable String workspaceId, ServerWebExchange exchange) {
        return ok(exchange, service.markRecentWorkspace(workspaceId, userId(exchange)));
    }

    @GetMapping("/personal-workspaces/{personalWorkspaceId}/diff")
    public ApiResponse<Object> diffPersonalWorkspace(@PathVariable String personalWorkspaceId, ServerWebExchange exchange) {
        return ok(exchange, service.diffPersonalWorkspace(personalWorkspaceId, userId(exchange)));
    }

    @PostMapping("/personal-workspaces/{personalWorkspaceId}/sync-to-application")
    public ApiResponse<Object> syncPersonalToApplication(
            @PathVariable String personalWorkspaceId,
            @RequestBody ManagedWorkspaceDtos.SyncWorkspaceRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.syncPersonalToApplication(
                personalWorkspaceId,
                request.files(),
                Boolean.TRUE.equals(request.force()),
                userId(exchange),
                RuntimeApiSupport.traceId(exchange)));
    }

    @PostMapping("/personal-workspaces/{personalWorkspaceId}/sync-from-application")
    public ApiResponse<Object> syncApplicationToPersonal(
            @PathVariable String personalWorkspaceId,
            @RequestBody ManagedWorkspaceDtos.SyncWorkspaceRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.syncApplicationToPersonal(
                personalWorkspaceId,
                request.files(),
                userId(exchange),
                RuntimeApiSupport.traceId(exchange)));
    }

    private UserId userId(ServerWebExchange exchange) {
        return AuthWebSupport.getAuthPrincipal(exchange).userId();
    }

    private ApiResponse<Object> ok(ServerWebExchange exchange, Object data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }
}
