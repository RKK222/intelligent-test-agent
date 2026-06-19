package com.example.testagent.api.web;

import com.example.testagent.workspace.FileContentResponse;
import com.example.testagent.workspace.FileStatusResponse;
import com.example.testagent.workspace.FileTreeEntryResponse;
import com.example.testagent.workspace.WorkspaceApplicationService;
import com.example.testagent.common.api.ApiResponse;
import com.example.testagent.common.pagination.PageResponse;
import com.example.testagent.domain.workspace.WorkspaceId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * Workspace HTTP Controller，只做协议转换和统一响应封装，业务逻辑委托给应用服务。
 */
@RestController
public class WorkspaceController {

    private final WorkspaceApplicationService workspaceService;

    public WorkspaceController(WorkspaceApplicationService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping({"/api/workspaces", "/api/internal/platform/workspace-management/workspaces"})
    public ApiResponse<RuntimeDtos.WorkspaceResponse> createWorkspace(
            @Valid @RequestBody RuntimeDtos.CreateWorkspaceRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(
                RuntimeDtos.WorkspaceResponse.from(workspaceService.createWorkspace(request.name(), request.rootPath(), traceId)),
                traceId);
    }

    @GetMapping({"/api/workspaces", "/api/internal/platform/workspace-management/workspaces"})
    public ApiResponse<PageResponse<RuntimeDtos.WorkspaceResponse>> listWorkspaces(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.workspacePage(workspaceService.listWorkspaces(RuntimeApiSupport.pageRequest(page, size))), traceId);
    }

    @GetMapping({"/api/workspaces/{workspaceId}", "/api/internal/platform/workspace-management/workspaces/{workspaceId}"})
    public ApiResponse<RuntimeDtos.WorkspaceResponse> getWorkspace(
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.WorkspaceResponse.from(workspaceService.getWorkspace(new WorkspaceId(workspaceId))), traceId);
    }

    @GetMapping({"/api/workspaces/{workspaceId}/files", "/api/internal/platform/workspace-management/workspaces/{workspaceId}/files"})
    public ApiResponse<List<FileTreeEntryResponse>> listFiles(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String path,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(workspaceService.listFiles(new WorkspaceId(workspaceId), path), traceId);
    }

    @GetMapping({"/api/workspaces/{workspaceId}/files/content", "/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/content"})
    public ApiResponse<FileContentResponse> readFile(
            @PathVariable String workspaceId,
            @RequestParam String path,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(workspaceService.readFile(new WorkspaceId(workspaceId), path), traceId);
    }

    @PutMapping({"/api/workspaces/{workspaceId}/files/content", "/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/content"})
    public ApiResponse<Void> writeFile(
            @PathVariable String workspaceId,
            @Valid @RequestBody RuntimeDtos.WriteFileRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        workspaceService.writeFile(new WorkspaceId(workspaceId), request.path(), request.content());
        return ApiResponse.ok(null, traceId);
    }

    @GetMapping({"/api/workspaces/{workspaceId}/files/status", "/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/status"})
    public ApiResponse<FileStatusResponse> fileStatus(
            @PathVariable String workspaceId,
            @RequestParam String path,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(workspaceService.fileStatus(new WorkspaceId(workspaceId), path), traceId);
    }
}
