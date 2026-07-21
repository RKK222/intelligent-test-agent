package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.workspace.ManagedWorkspaceApplicationService;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 版本切换前只读校验当前用户是否可以访问关联 Git 版本库，不创建或修改本地工作区。
     */
    @GetMapping("/workspace-versions/{versionId}/git-access")
    public ApiResponse<Object> checkVersionGitAccess(
            @PathVariable String versionId,
            ServerWebExchange exchange) {
        return ok(exchange, service.checkVersionGitAccess(versionId, userId(exchange)));
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
        return ok(exchange, service.diffPersonalWorkspace(personalWorkspaceId, userId(exchange), RuntimeApiSupport.traceId(exchange)));
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

    @PostMapping("/workspaces/{workspaceId}/git-discard")
    public ApiResponse<Object> discardWorkspaceGitFiles(
            @PathVariable String workspaceId,
            @RequestBody ManagedWorkspaceDtos.WorkspaceGitFilesRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requirePersonalWorkspacePathPermission(exchange, request.files());
        service.discardWorkspaceGitFiles(
                workspaceId,
                request.files(),
                principal.userId(),
                RuntimeApiSupport.traceId(exchange));
        return ok(exchange, null);
    }

    @PostMapping("/workspaces/{workspaceId}/git-stage")
    public ApiResponse<Object> stageWorkspaceGitFiles(
            @PathVariable String workspaceId,
            @RequestBody ManagedWorkspaceDtos.WorkspaceGitFilesRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requirePersonalWorkspacePathPermission(exchange, request.files());
        service.stageWorkspaceGitFiles(workspaceId, request.files(), principal.userId());
        return ok(exchange, null);
    }

    @PostMapping("/workspaces/{workspaceId}/git-unstage")
    public ApiResponse<Object> unstageWorkspaceGitFiles(
            @PathVariable String workspaceId,
            @RequestBody ManagedWorkspaceDtos.WorkspaceGitFilesRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requirePersonalWorkspacePathPermission(exchange, request.files());
        service.unstageWorkspaceGitFiles(workspaceId, request.files(), principal.userId());
        return ok(exchange, null);
    }

    @GetMapping("/workspaces/{workspaceId}/git-conflict")
    public ApiResponse<Object> getWorkspaceGitConflict(
            @PathVariable String workspaceId,
            @RequestParam String path,
            ServerWebExchange exchange) {
        return ok(exchange, service.getWorkspaceGitConflict(workspaceId, path, userId(exchange)));
    }

    @PostMapping("/workspaces/{workspaceId}/git-conflict/resolve")
    public ApiResponse<Object> resolveWorkspaceGitConflict(
            @PathVariable String workspaceId,
            @RequestBody ManagedWorkspaceDtos.ResolveWorkspaceGitConflictRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requirePersonalWorkspacePathPermission(exchange, List.of(request.path()));
        service.resolveWorkspaceGitConflict(
                workspaceId,
                request.path(),
                request.resolution(),
                request.content(),
                principal.userId());
        return ok(exchange, null);
    }

    @PostMapping("/workspaces/{workspaceId}/git-conflict/abort")
    public ApiResponse<Object> abortWorkspaceGitConflict(
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireWorkspaceConflictPermission(exchange, workspaceId);
        service.abortWorkspaceGitConflict(workspaceId, principal.userId());
        return ok(exchange, null);
    }

    @PostMapping("/workspaces/{workspaceId}/git-conflict/resolve-all")
    public ApiResponse<Object> resolveAllWorkspaceGitConflicts(
            @PathVariable String workspaceId,
            @RequestBody ManagedWorkspaceDtos.ResolveAllWorkspaceGitConflictsRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireWorkspaceConflictPermission(exchange, workspaceId);
        service.resolveAllWorkspaceGitConflicts(workspaceId, request.resolution(), principal.userId());
        return ok(exchange, null);
    }

    /** 全部冲突解决后提交完整 merge index；不得走会 reset index 的普通文件提交入口。 */
    @PostMapping("/workspaces/{workspaceId}/git-conflict/complete")
    public ApiResponse<Object> completeWorkspaceGitMerge(
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requireWorkspaceMergeCompletionPermission(exchange, workspaceId);
        return ok(exchange, service.completeWorkspaceGitMerge(
                workspaceId,
                principal.userId(),
                RuntimeApiSupport.traceId(exchange)));
    }

    @PostMapping("/personal-workspaces/{personalWorkspaceId}/publish-preview")
    public ApiResponse<Object> previewPersonalWorkspacePublish(
            @PathVariable String personalWorkspaceId,
            ServerWebExchange exchange) {
        return ok(exchange, service.previewPersonalWorkspacePublish(
                personalWorkspaceId,
                userId(exchange),
                RuntimeApiSupport.traceId(exchange)));
    }

    /**
     * 个人工作区"提交并推送"：先读取个人 HEAD，再把白名单文件投影到应用 feature worktree，提交并推送。
     * 未选中的 spec 或其它个人文件不会随个人分支合并进入应用版本。
     */
    @PostMapping("/personal-workspaces/{personalWorkspaceId}/publish")
    public ApiResponse<Object> publishPersonalWorkspace(
            @PathVariable String personalWorkspaceId,
            @RequestBody ManagedWorkspaceDtos.PublishPersonalWorkspaceRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requirePersonalWorkspacePathPermission(exchange, request.files());
        return ok(exchange, service.publishPersonalWorkspace(
                personalWorkspaceId,
                request.commitMessage(),
                request.files(),
                request.expectedApplicationHead(),
                request.operationId(),
                principal.userId(),
                RuntimeApiSupport.traceId(exchange)));
    }

    /** 仅提交当前个人 worktree；推送必须由后续发布接口从个人 HEAD 投影到 feature worktree。 */
    @PostMapping("/personal-workspaces/{personalWorkspaceId}/commit")
    public ApiResponse<Object> commitPersonalWorkspace(
            @PathVariable String personalWorkspaceId,
            @RequestBody ManagedWorkspaceDtos.PublishPersonalWorkspaceRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = requirePersonalWorkspacePathPermission(exchange, request.files());
        return ok(exchange, service.commitPersonalWorkspace(
                personalWorkspaceId,
                request.commitMessage(),
                request.files(),
                principal.userId(),
                RuntimeApiSupport.traceId(exchange)));
    }

    /**
     * 个人 worktree 只是物理隔离，目录写权限仍由平台后端兜底。
     * `.opencode/**` 只能由应用管理员提交或发布，避免绕过 AgentConfig 写接口的角色校验。
     */
    private AuthPrincipal requirePersonalWorkspacePathPermission(ServerWebExchange exchange, List<String> files) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        List<String> protectedFiles = files == null ? List.of() : files.stream()
                .filter(this::isApplicationAgentConfigPath)
                .toList();
        if (!protectedFiles.isEmpty() && !AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN)) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "应用 Agent 配置仅允许应用管理员提交或发布",
                    Map.of("files", protectedFiles));
        }
        return principal;
    }

    /** 批量解决或取消 merge 前检查当前冲突中是否包含受保护的应用 Agent 配置。 */
    private AuthPrincipal requireWorkspaceConflictPermission(ServerWebExchange exchange, String workspaceId) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        if (AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN)) {
            return principal;
        }
        var diff = service.getWorkspaceGitDiff(workspaceId, principal.userId());
        List<String> protectedFiles = diff == null ? List.of() : diff.files().stream()
                .filter(file -> "conflict".equalsIgnoreCase(file.status()))
                .map(com.enterprise.testagent.workspace.ManagedWorkspaceResponses.WorkspaceGitDiffFileResponse::path)
                .filter(this::isApplicationAgentConfigPath)
                .toList();
        if (!protectedFiles.isEmpty() && !AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN)) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "应用 Agent 配置冲突仅允许应用管理员处理",
                    Map.of("files", protectedFiles));
        }
        return principal;
    }

    /** 合并完成时冲突状态已经消失，因此要检查完整 merge diff 中是否仍包含受保护的应用配置。 */
    private AuthPrincipal requireWorkspaceMergeCompletionPermission(
            ServerWebExchange exchange,
            String workspaceId) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        if (AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN)) {
            return principal;
        }
        var diff = service.getWorkspaceGitDiff(workspaceId, principal.userId());
        List<String> protectedFiles = diff == null ? List.of() : diff.files().stream()
                .map(com.enterprise.testagent.workspace.ManagedWorkspaceResponses.WorkspaceGitDiffFileResponse::path)
                .filter(this::isApplicationAgentConfigPath)
                .toList();
        if (!protectedFiles.isEmpty()) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "包含应用 Agent 配置的合并仅允许应用管理员完成",
                    Map.of("files", protectedFiles));
        }
        return principal;
    }

    private boolean isApplicationAgentConfigPath(String file) {
        if (file == null || file.isBlank()) {
            return false;
        }
        try {
            String normalized = Path.of(file.replace('\\', '/')).normalize().toString().replace('\\', '/');
            while (normalized.startsWith("./")) {
                normalized = normalized.substring(2);
            }
            return normalized.equals(".opencode") || normalized.startsWith(".opencode/");
        } catch (RuntimeException exception) {
            // 非法路径由业务服务统一返回参数错误；权限判断不在此改变错误契约。
            return false;
        }
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
