package com.example.testagent.api.web.platform;

import com.example.testagent.api.web.common.RuntimeApiSupport;
import com.example.testagent.workspace.FileContentResponse;
import com.example.testagent.workspace.FileStatusResponse;
import com.example.testagent.workspace.FileTreeEntryResponse;
import com.example.testagent.workspace.WorkspaceApplicationService;
import com.example.testagent.workspace.WorkspaceDirectoryListResponse;
import com.example.testagent.workspace.WorkspaceDirectoryService;
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
    private final WorkspaceDirectoryService directoryService;

    /**
     * 注入工作区应用服务和目录选择服务，Controller 只负责 HTTP 协议适配。
     */
    public WorkspaceController(WorkspaceApplicationService workspaceService, WorkspaceDirectoryService directoryService) {
        this.workspaceService = workspaceService;
        this.directoryService = directoryService;
    }

    /**
     * 创建工作区，并保持公开路径与内部平台路径响应契约一致。
     */
    @PostMapping({"/api/workspaces", "/api/internal/platform/workspace-management/workspaces"})
    public ApiResponse<RuntimeDtos.WorkspaceResponse> createWorkspace(
            @Valid @RequestBody RuntimeDtos.CreateWorkspaceRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(
                RuntimeDtos.WorkspaceResponse.from(workspaceService.createWorkspace(request.name(), request.rootPath(), traceId)),
                traceId);
    }

    /**
     * 分页列出工作区，分页参数统一交给 RuntimeApiSupport 校验和默认化。
     */
    @GetMapping({"/api/workspaces", "/api/internal/platform/workspace-management/workspaces"})
    public ApiResponse<PageResponse<RuntimeDtos.WorkspaceResponse>> listWorkspaces(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.workspacePage(workspaceService.listWorkspaces(RuntimeApiSupport.pageRequest(page, size))), traceId);
    }

    /**
     * 浏览允许范围内的本机目录，用于前端选择新的 Workspace 根目录。
     */
    @GetMapping({"/api/workspace-directories", "/api/internal/platform/workspace-management/workspace-directories"})
    public ApiResponse<WorkspaceDirectoryListResponse> listWorkspaceDirectories(
            @RequestParam(required = false) String path,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(directoryService.listDirectories(path), traceId);
    }

    /**
     * 查询单个工作区详情，路径参数在 HTTP 边界转换为领域 ID。
     */
    @GetMapping({"/api/workspaces/{workspaceId}", "/api/internal/platform/workspace-management/workspaces/{workspaceId}"})
    public ApiResponse<RuntimeDtos.WorkspaceResponse> getWorkspace(
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(RuntimeDtos.WorkspaceResponse.from(workspaceService.getWorkspace(new WorkspaceId(workspaceId))), traceId);
    }

    /**
     * 列出工作区内文件树，path 为空时由应用层解释为工作区根目录。
     */
    @GetMapping({"/api/workspaces/{workspaceId}/files", "/api/internal/platform/workspace-management/workspaces/{workspaceId}/files"})
    public ApiResponse<List<FileTreeEntryResponse>> listFiles(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String path,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(workspaceService.listFiles(new WorkspaceId(workspaceId), path), traceId);
    }

    /**
     * 读取工作区文件内容，路径安全校验由 workspace 应用服务统一执行。
     */
    @GetMapping({"/api/workspaces/{workspaceId}/files/content", "/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/content"})
    public ApiResponse<FileContentResponse> readFile(
            @PathVariable String workspaceId,
            @RequestParam String path,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(workspaceService.readFile(new WorkspaceId(workspaceId), path), traceId);
    }

    /**
     * 写入工作区文件内容，Controller 不直接触碰文件系统。
     */
    @PutMapping({"/api/workspaces/{workspaceId}/files/content", "/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/content"})
    public ApiResponse<Void> writeFile(
            @PathVariable String workspaceId,
            @Valid @RequestBody RuntimeDtos.WriteFileRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        workspaceService.writeFile(new WorkspaceId(workspaceId), request.path(), request.content());
        return ApiResponse.ok(null, traceId);
    }

    /**
     * 查询单个文件状态，用于前端在写入或展示前判断文件是否存在及类型。
     */
    @GetMapping({"/api/workspaces/{workspaceId}/files/status", "/api/internal/platform/workspace-management/workspaces/{workspaceId}/files/status"})
    public ApiResponse<FileStatusResponse> fileStatus(
            @PathVariable String workspaceId,
            @RequestParam String path,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(workspaceService.fileStatus(new WorkspaceId(workspaceId), path), traceId);
    }
}
