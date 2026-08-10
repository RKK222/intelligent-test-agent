package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.workspace.WorkspaceApplicationService;
import com.enterprise.testagent.workspace.UserWorkspaceQueryService;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final ManagedWorkspacePathResolver pathResolver;
    private final SessionCollaborationShareService shareService;

    /**
     * 注入工作区应用服务，Controller 只负责 HTTP 协议适配。
     */
    public WorkspaceController(WorkspaceApplicationService workspaceService) {
        this(workspaceService, null, ManagedWorkspacePathResolver.legacyOnly(), null);
    }

    /** 兼容既有测试构造；普通请求仍执行对象级归属校验。 */
    public WorkspaceController(
            WorkspaceApplicationService workspaceService,
            UserWorkspaceQueryService userWorkspaceQueryService,
            ManagedWorkspacePathResolver pathResolver) {
        this(workspaceService, userWorkspaceQueryService, pathResolver, null);
    }

    /** 生产入口同时注入分享服务，分享读取始终校验精确 workspace 范围。 */
    @Autowired
    public WorkspaceController(
            WorkspaceApplicationService workspaceService,
            UserWorkspaceQueryService userWorkspaceQueryService,
            ManagedWorkspacePathResolver pathResolver,
            SessionCollaborationShareService shareService) {
        this.workspaceService = workspaceService;
        this.userWorkspaceQueryService = userWorkspaceQueryService;
        this.pathResolver = pathResolver;
        this.shareService = shareService;
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
                : userWorkspaceQueryService.listUserWorkspaces(principal.userId(), pageRequest), pathResolver), traceId);
    }

    /**
     * 查询单个工作区详情，路径参数在 HTTP 边界转换为领域 ID。
     */
    @GetMapping("/api/internal/platform/workspace-management/workspaces/{workspaceId}")
    public ApiResponse<RuntimeDtos.WorkspaceResponse> getWorkspace(
            @PathVariable String workspaceId,
            @RequestHeader(name = SessionShareController.SHARE_HEADER, required = false) String shareId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        var principal = AuthWebSupport.getAuthPrincipal(exchange);
        WorkspaceId requested = new WorkspaceId(workspaceId);
        if (shareId == null || shareId.isBlank()) {
            return ApiResponse.ok(RuntimeDtos.WorkspaceResponse.from(userWorkspaceQueryService == null
                    ? workspaceService.getWorkspace(requested)
                    : userWorkspaceQueryService.requireUserWorkspace(principal.userId(), requested), pathResolver), traceId);
        }
        if (shareService == null) {
            throw new com.enterprise.testagent.common.error.PlatformException(
                    com.enterprise.testagent.common.error.ErrorCode.RUNTIME_STATE_UNAVAILABLE,
                    "会话分享服务未配置");
        }
        DelegatedOperationContext context = shareService.requireAccess(
                principal.userId(), new SessionShareId(shareId), false, traceId);
        context.requireWorkspace(requested);
        try {
            var workspace = workspaceService.getWorkspace(requested);
            shareService.recordOperation(
                    context, "WORKSPACE_READ", "WORKSPACE", requested.value(), null,
                    "SUCCESS", null, traceId);
            return ApiResponse.ok(RuntimeDtos.WorkspaceResponse.from(workspace, pathResolver), traceId);
        } catch (RuntimeException failure) {
            shareService.recordOperation(
                    context, "WORKSPACE_READ", "WORKSPACE", requested.value(), null,
                    failure instanceof com.enterprise.testagent.common.error.PlatformException
                            ? "DENIED" : "FAILED",
                    failure instanceof com.enterprise.testagent.common.error.PlatformException platform
                            ? platform.errorCode().name() : "WORKSPACE_READ_FAILED",
                    traceId);
            throw failure;
        }
    }

}
