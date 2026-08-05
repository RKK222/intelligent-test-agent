package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.workspace.WorkspaceApplicationService;
import com.enterprise.testagent.workspace.UserWorkspaceQueryService;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.beans.factory.annotation.Autowired;
import com.enterprise.testagent.api.web.common.AuthWebSupport;

/**
 * Workspace HTTP Controller，只做协议转换和统一响应封装，业务逻辑委托给应用服务。
 */
@RestController
public class WorkspaceController {

    private final WorkspaceApplicationService workspaceService;
    private final UserWorkspaceQueryService userWorkspaceQueryService;

    /**
     * 注入工作区应用服务，Controller 只负责 HTTP 协议适配。
     */
    public WorkspaceController(WorkspaceApplicationService workspaceService) {
        this(workspaceService, null);
    }

    /** 生产入口注入用户工作区查询服务，所有普通列表和详情都执行对象级归属校验。 */
    @Autowired
    public WorkspaceController(
            WorkspaceApplicationService workspaceService,
            UserWorkspaceQueryService userWorkspaceQueryService) {
        this.workspaceService = workspaceService;
        this.userWorkspaceQueryService = userWorkspaceQueryService;
    }

    /**
     * 分页列出工作区，分页参数统一交给 RuntimeApiSupport 校验和默认化。
     */
    @GetMapping("/api/internal/platform/workspace-management/workspaces")
    public ApiResponse<PageResponse<RuntimeDtos.WorkspaceResponse>> listWorkspaces(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        var pageRequest = RuntimeApiSupport.pageRequest(page, size);
        var principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(RuntimeDtos.workspacePage(userWorkspaceQueryService == null
                ? workspaceService.listWorkspaces(pageRequest)
                : userWorkspaceQueryService.listUserWorkspaces(principal.userId(), pageRequest)), traceId);
    }

    /**
     * 查询单个工作区详情，路径参数在 HTTP 边界转换为领域 ID。
     */
    @GetMapping("/api/internal/platform/workspace-management/workspaces/{workspaceId}")
    public ApiResponse<RuntimeDtos.WorkspaceResponse> getWorkspace(
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        var principal = AuthWebSupport.getAuthPrincipal(exchange);
        return ApiResponse.ok(RuntimeDtos.WorkspaceResponse.from(userWorkspaceQueryService == null
                ? workspaceService.getWorkspace(new WorkspaceId(workspaceId))
                : userWorkspaceQueryService.requireUserWorkspace(principal.userId(), new WorkspaceId(workspaceId))), traceId);
    }

}
