package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import com.enterprise.testagent.opencode.runtime.share.SessionCollaborationShareService;
import com.enterprise.testagent.workspace.ManagedWorkspaceApplicationService;
import com.enterprise.testagent.workspace.ManagedWorkspaceGitPathPolicy;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final SessionCollaborationShareService shareService;

    public ManagedWorkspaceController(
            ManagedWorkspaceApplicationService service,
            UserOpencodeProcessAssignmentService processAssignmentService) {
        this(service, processAssignmentService, null);
    }

    @Autowired
    public ManagedWorkspaceController(
            ManagedWorkspaceApplicationService service,
            UserOpencodeProcessAssignmentService processAssignmentService,
            SessionCollaborationShareService shareService) {
        this.service = service;
        this.processAssignmentService = processAssignmentService;
        this.shareService = shareService;
    }

    @GetMapping("/applications")
    public ApiResponse<Object> listApplications(ServerWebExchange exchange) {
        return ok(exchange, service.listApplications(userId(exchange)));
    }

    /** 应用管理员按成员范围查看 Git 刷新范围；超级管理员继续查看全部应用。 */
    @GetMapping("/applications/git-refresh-scopes")
    public ApiResponse<Object> listApplicationGitRefreshScopes(ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        return ok(exchange, service.listApplicationGitRefreshScopes(
                principal.userId(),
                AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN)));
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

    /** 应用管理员刷新有成员权限的应用；超级管理员保持全量刷新能力。 */
    @PostMapping("/applications/{appId}/git-refresh")
    public ApiResponse<Object> refreshApplicationGit(
            @PathVariable String appId,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        return ok(exchange, service.refreshApplicationGit(
                appId,
                principal.userId(),
                AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN),
                RuntimeApiSupport.traceId(exchange)));
    }

    /** 应用管理员只刷新有成员权限应用中的一个实际 feature 分支组。 */
    @PostMapping("/applications/{appId}/git-refresh-groups")
    public ApiResponse<Object> refreshApplicationGitGroup(
            @PathVariable String appId,
            @RequestBody ManagedWorkspaceDtos.RefreshApplicationGitGroupRequest request,
            ServerWebExchange exchange) {
        AuthPrincipal principal = AuthWebSupport.requireRole(exchange, Dictionary.ROLE_APP_ADMIN);
        return ok(exchange, service.refreshApplicationGitGroup(
                appId,
                request.repositoryId(),
                request.version(),
                request.branch(),
                principal.userId(),
                AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN),
                RuntimeApiSupport.traceId(exchange)));
    }

    /** 拉取只作用于当前登录用户拥有的个人 worktree，不更新应用版本或其它用户。 */
    @PostMapping("/personal-workspaces/{personalWorkspaceId}/git-pull")
    public ApiResponse<Object> gitPullPersonalWorkspace(
            @PathVariable String personalWorkspaceId,
            ServerWebExchange exchange) {
        return ok(exchange, service.gitPullPersonalWorkspace(
                personalWorkspaceId,
                userId(exchange),
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
        // 个人 worktree 必须创建在用户进程所在服务器；未初始化时禁止按入口 Java 本机归属落盘。
        readyAgentProcess(exchange);
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
        // 修复已有 default 也可能发生文件落盘，必须与新建路径使用同一 READY 进程守卫。
        readyAgentProcess(exchange);
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
        return ok(exchange, workspaceShareAction(
                exchange, workspaceId, false, "WORKSPACE_GIT_DIFF_READ",
                (executionUser, context) -> service.getWorkspaceGitDiff(workspaceId, executionUser)));
    }

    @PostMapping("/workspaces/{workspaceId}/git-discard")
    public ApiResponse<Object> discardWorkspaceGitFiles(
            @PathVariable String workspaceId,
            @RequestBody ManagedWorkspaceDtos.WorkspaceGitFilesRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, workspaceShareAction(
                exchange, workspaceId, true, "WORKSPACE_GIT_DISCARD",
                (executionUser, context) -> {
                    requireWorkspaceGitPathPermission(exchange, context, request.files());
                    service.discardWorkspaceGitFiles(
                            workspaceId, request.files(), executionUser, RuntimeApiSupport.traceId(exchange));
                    return null;
                }));
    }

    @PostMapping("/workspaces/{workspaceId}/git-stage")
    public ApiResponse<Object> stageWorkspaceGitFiles(
            @PathVariable String workspaceId,
            @RequestBody ManagedWorkspaceDtos.WorkspaceGitFilesRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, workspaceShareAction(
                exchange, workspaceId, true, "WORKSPACE_GIT_STAGE",
                (executionUser, context) -> {
                    requireWorkspaceGitPathPermission(exchange, context, request.files());
                    service.stageWorkspaceGitFiles(workspaceId, request.files(), executionUser);
                    return null;
                }));
    }

    @PostMapping("/workspaces/{workspaceId}/git-unstage")
    public ApiResponse<Object> unstageWorkspaceGitFiles(
            @PathVariable String workspaceId,
            @RequestBody ManagedWorkspaceDtos.WorkspaceGitFilesRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, workspaceShareAction(
                exchange, workspaceId, true, "WORKSPACE_GIT_UNSTAGE",
                (executionUser, context) -> {
                    requireWorkspaceGitPathPermission(exchange, context, request.files());
                    service.unstageWorkspaceGitFiles(workspaceId, request.files(), executionUser);
                    return null;
                }));
    }

    @GetMapping("/workspaces/{workspaceId}/git-conflict")
    public ApiResponse<Object> getWorkspaceGitConflict(
            @PathVariable String workspaceId,
            @RequestParam String path,
            ServerWebExchange exchange) {
        return ok(exchange, workspaceShareAction(
                exchange, workspaceId, false, "WORKSPACE_GIT_CONFLICT_READ",
                (executionUser, context) -> service.getWorkspaceGitConflict(workspaceId, path, executionUser)));
    }

    @PostMapping("/workspaces/{workspaceId}/git-conflict/resolve")
    public ApiResponse<Object> resolveWorkspaceGitConflict(
            @PathVariable String workspaceId,
            @RequestBody ManagedWorkspaceDtos.ResolveWorkspaceGitConflictRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, workspaceShareAction(
                exchange, workspaceId, true, "WORKSPACE_GIT_CONFLICT_RESOLVE",
                (executionUser, context) -> {
                    requireWorkspaceGitPathPermission(exchange, context, List.of(request.path()));
                    service.resolveWorkspaceGitConflict(
                            workspaceId, request.path(), request.resolution(), request.content(), executionUser);
                    return null;
                }));
    }

    @PostMapping("/workspaces/{workspaceId}/git-conflict/abort")
    public ApiResponse<Object> abortWorkspaceGitConflict(
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        return ok(exchange, workspaceShareAction(
                exchange, workspaceId, true, "WORKSPACE_GIT_CONFLICT_ABORT",
                (executionUser, context) -> {
                    requireWorkspaceConflictPermission(exchange, workspaceId, executionUser, context);
                    service.abortWorkspaceGitConflict(workspaceId, executionUser);
                    return null;
                }));
    }

    @PostMapping("/workspaces/{workspaceId}/git-conflict/resolve-all")
    public ApiResponse<Object> resolveAllWorkspaceGitConflicts(
            @PathVariable String workspaceId,
            @RequestBody ManagedWorkspaceDtos.ResolveAllWorkspaceGitConflictsRequest request,
            ServerWebExchange exchange) {
        return ok(exchange, workspaceShareAction(
                exchange, workspaceId, true, "WORKSPACE_GIT_CONFLICT_RESOLVE_ALL",
                (executionUser, context) -> {
                    requireWorkspaceConflictPermission(exchange, workspaceId, executionUser, context);
                    service.resolveAllWorkspaceGitConflicts(workspaceId, request.resolution(), executionUser);
                    return null;
                }));
    }

    /** 全部冲突解决后提交完整 merge index；不得走会 reset index 的普通文件提交入口。 */
    @PostMapping("/workspaces/{workspaceId}/git-conflict/complete")
    public ApiResponse<Object> completeWorkspaceGitMerge(
            @PathVariable String workspaceId,
            ServerWebExchange exchange) {
        return ok(exchange, workspaceShareAction(
                exchange, workspaceId, true, "WORKSPACE_GIT_MERGE_COMPLETE",
                (executionUser, context) -> {
                    requireWorkspaceMergeCompletionPermission(exchange, workspaceId, executionUser, context);
                    return service.completeWorkspaceGitMerge(
                            workspaceId, executionUser, RuntimeApiSupport.traceId(exchange));
                }));
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
        ManagedWorkspaceGitPathPolicy.requireWriteAccess(files, principal.roles());
        return principal;
    }

    /** 分享成员不得借 Git 接口修改 owner-only 的 Agent 配置；普通请求继续沿用角色策略。 */
    private void requireWorkspaceGitPathPermission(
            ServerWebExchange exchange,
            DelegatedOperationContext context,
            List<String> files) {
        if (context == null) {
            requirePersonalWorkspacePathPermission(exchange, files);
            return;
        }
        List<String> protectedFiles = files.stream()
                .filter(ManagedWorkspaceGitPathPolicy::isApplicationConfigPath)
                .toList();
        if (!protectedFiles.isEmpty()) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "分享成员不能修改应用 Agent 配置",
                    Map.of("files", protectedFiles));
        }
    }

    /** 批量解决或取消 merge 前检查当前冲突中是否包含受保护的应用 Agent 配置。 */
    private AuthPrincipal requireWorkspaceConflictPermission(ServerWebExchange exchange, String workspaceId) {
        return requireWorkspaceConflictPermission(exchange, workspaceId, userId(exchange), null);
    }

    private AuthPrincipal requireWorkspaceConflictPermission(
            ServerWebExchange exchange,
            String workspaceId,
            UserId executionUser,
            DelegatedOperationContext context) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        if (context == null && AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN)) {
            return principal;
        }
        var diff = service.getWorkspaceGitDiff(workspaceId, executionUser);
        List<String> protectedFiles = diff == null ? List.of() : diff.files().stream()
                .filter(file -> "conflict".equalsIgnoreCase(file.status()))
                .map(com.enterprise.testagent.workspace.ManagedWorkspaceResponses.WorkspaceGitDiffFileResponse::path)
                .filter(this::isApplicationAgentConfigPath)
                .toList();
        if (!protectedFiles.isEmpty()
                && (context != null || !AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN))) {
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
        return requireWorkspaceMergeCompletionPermission(exchange, workspaceId, userId(exchange), null);
    }

    private AuthPrincipal requireWorkspaceMergeCompletionPermission(
            ServerWebExchange exchange,
            String workspaceId,
            UserId executionUser,
            DelegatedOperationContext context) {
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        if (context == null && AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN)) {
            return principal;
        }
        var diff = service.getWorkspaceGitDiff(workspaceId, executionUser);
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
        return ManagedWorkspaceGitPathPolicy.isApplicationConfigPath(file);
    }

    /** 精确分享范围解析后，把业务执行用户显式替换为会话所属人，真实 principal 始终不变。 */
    private Object workspaceShareAction(
            ServerWebExchange exchange,
            String workspaceId,
            boolean requireChat,
            String auditAction,
            BiFunction<UserId, DelegatedOperationContext, Object> action) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        AuthPrincipal principal = AuthWebSupport.getAuthPrincipal(exchange);
        String shareId = exchange.getRequest().getHeaders().getFirst(SessionShareController.SHARE_HEADER);
        if (shareId == null || shareId.isBlank()) {
            return action.apply(principal.userId(), null);
        }
        if (shareService == null) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "会话分享服务未配置");
        }
        DelegatedOperationContext context = shareService.requireAccess(
                principal.userId(), new SessionShareId(shareId), requireChat, traceId);
        context.requireWorkspace(new WorkspaceId(workspaceId));
        try {
            Object result = action.apply(context.executionOwnerUserId(), context);
            shareService.recordOperation(
                    context, auditAction, "WORKSPACE", workspaceId, null,
                    "SUCCESS", null, traceId);
            return result;
        } catch (RuntimeException failure) {
            shareService.recordOperation(
                    context, auditAction, "WORKSPACE", workspaceId, null,
                    failure instanceof PlatformException ? "DENIED" : "FAILED",
                    failure instanceof PlatformException platform
                            ? platform.errorCode().name() : "WORKSPACE_GIT_OPERATION_FAILED",
                    traceId);
            throw failure;
        }
    }

    private UserId userId(ServerWebExchange exchange) {
        return AuthWebSupport.getAuthPrincipal(exchange).userId();
    }

    private String agentLinuxServerId(ServerWebExchange exchange) {
        UserOpencodeProcessAssignment assignment = readyAgentProcess(exchange);
        if (assignment.linuxServerId() != null) {
            return assignment.linuxServerId();
        }
        try {
            return URI.create(assignment.node().baseUrl()).getHost();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private UserOpencodeProcessAssignment readyAgentProcess(ServerWebExchange exchange) {
        return processAssignmentService.requireReadyProcess(
                userId(exchange),
                "opencode",
                RuntimeApiSupport.traceId(exchange));
    }

    private ApiResponse<Object> ok(ServerWebExchange exchange, Object data) {
        return ApiResponse.ok(data, RuntimeApiSupport.traceId(exchange));
    }
}
