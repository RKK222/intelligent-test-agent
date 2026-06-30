package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.api.web.common.RuntimeApiSupport;
import com.icbc.testagent.common.api.ApiResponse;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.icbc.testagent.workspace.ManagedWorkspaceApplicationService;
import java.net.URI;
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
    private final UserOpencodeProcessAssignmentService processAssignmentService;

    public ManagedWorkspaceController(
            ManagedWorkspaceApplicationService service,
            UserOpencodeProcessAssignmentService processAssignmentService) {
        this.service = service;
        this.processAssignmentService = processAssignmentService;
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
                agentLinuxServerId(exchange),
                RuntimeApiSupport.traceId(exchange)));
    }

    @PostMapping("/workspace-versions/{versionId}/git-pull")
    public ApiResponse<Object> gitPullVersion(
            @PathVariable String versionId,
            ServerWebExchange exchange) {
        return ok(exchange, service.gitPullVersion(
                versionId,
                userId(exchange),
                agentLinuxServerId(exchange),
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

    /**
     * 标记当前用户在某 (appId, workspaceId) 维度下最近一次手动选择的 VCS 分支，
     * 用于下次进入同一工作区时自动回填分支显示。
     */
    @PostMapping("/applications/{appId}/workspaces/{workspaceId}/branch-preference")
    public ApiResponse<Object> markRecentBranch(
            @PathVariable String appId,
            @PathVariable String workspaceId,
            @RequestBody ManagedWorkspaceDtos.BranchPreferenceRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.markRecentBranch(appId, workspaceId, request.branch(), userId(exchange)));
    }

    /**
     * 查询当前用户在某 (appId, workspaceId) 维度下的最近 VCS 分支偏好；未设置时返回 null。
     */
    @GetMapping("/applications/{appId}/workspaces/{workspaceId}/branch-preference")
    public ApiResponse<Object> recentBranch(
            @PathVariable String appId,
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        return ok(exchange, service.recentBranch(appId, workspaceId, userId(exchange)).orElse(null));
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

    /**
     * 确保默认个人工作区存在：先查 (versionId, userId, workspaceName=default)，
     * 存在则复用，不存在则后台创建。分支命名: {应用版本分支}_{userId}_default。
     */
    @PostMapping("/workspace-versions/{versionId}/ensure-default-personal-workspace")
    public ApiResponse<Object> ensureDefaultPersonalWorkspace(
            @PathVariable String versionId,
            ServerWebExchange exchange) {
        return ok(exchange, service.ensureDefaultPersonalWorkspace(
                versionId,
                userId(exchange),
                RuntimeApiSupport.traceId(exchange)));
    }

    /**
     * 基于本地 Git（不依赖 opencode runtime）获取工作区变更文件列表。
     * 通过 runtime workspace 反查 personal workspace，使用其 repoRoot 进行 git status --porcelain + git diff。
     */
    @GetMapping("/workspaces/{workspaceId}/git-diff")
    public ApiResponse<Object> getWorkspaceGitDiff(
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        return ok(exchange, service.getWorkspaceGitDiff(workspaceId, userId(exchange)));
    }

    /**
     * 个人工作区"提交并推送"：将个人 worktree 合并回应用版本分支。
     * 合并成功: 更新版本 commit；冲突: 返回 CONFLICT + 冲突文件列表。
     */
    @PostMapping("/personal-workspaces/{personalWorkspaceId}/publish")
    public ApiResponse<Object> publishPersonalWorkspace(
            @PathVariable String personalWorkspaceId,
            @RequestBody ManagedWorkspaceDtos.PublishPersonalWorkspaceRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, service.publishPersonalWorkspace(
                personalWorkspaceId,
                request.commitMessage(),
                userId(exchange),
                RuntimeApiSupport.traceId(exchange)));
    }

    private UserId userId(ServerWebExchange exchange) {
        return AuthWebSupport.getAuthPrincipal(exchange).userId();
    }

    private String agentLinuxServerId(ServerWebExchange exchange) {
        UserOpencodeProcessAssignment assignment = processAssignmentService.requireReadyProcess(
                userId(exchange),
                "opencode",
                RuntimeApiSupport.traceId(exchange));
        if (assignment.linuxServerId() != null) {
            return assignment.linuxServerId();
        }
        try {
            return URI.create(assignment.node().baseUrl()).getHost();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private ApiResponse<Object> ok(ServerWebExchange exchange, Object data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }
}
