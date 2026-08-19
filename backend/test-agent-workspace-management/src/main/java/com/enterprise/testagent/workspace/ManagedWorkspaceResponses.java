package com.enterprise.testagent.workspace;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspace;
import com.enterprise.testagent.domain.configuration.WorkspaceCreateOperation;
import com.enterprise.testagent.domain.configuration.WorkspaceCreateOperationStatus;
import com.enterprise.testagent.domain.configuration.WorkspaceCreateOperationStep;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.UserWorkspaceBranchPreference;
import com.enterprise.testagent.domain.workspace.Workspace;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 托管工作区应用服务响应模型，供 API 层直接转换为平台 DTO。
 */
public final class ManagedWorkspaceResponses {

    private ManagedWorkspaceResponses() {
    }

    public record ManagedApplicationResponse(String appId, String appName, boolean enabled) {
        public static ManagedApplicationResponse from(ApplicationDefinition application) {
            return new ManagedApplicationResponse(application.appId().value(), application.appName(), application.enabled());
        }
    }

    /**
     * 托管工作区对应的运行态 Workspace 响应。
     *
     * <p>{@code appId} / {@code versionId} / {@code applicationWorkspaceId} 仅在需要回答「这个工作区归属于哪个托管应用 / 版本 / 模板」时填充；
     * 当前由「最近工作区」相关接口（{@code recent-workspace} 与 {@code applications/{appId}/recent-workspace}）显式写入，
     * 便于前端在重新登录或换电脑登录时直接还原上次的「应用 + 模板 + 版本」上下文，让左下角"切换工作空间"按钮立刻显示当前所在的工作区。
     * 其他场景下传 {@code null}，避免引入反向依赖（运行态 Workspace 本身不强制绑定托管应用）。
     */
    public record WorkspaceRuntimeResponse(
            String workspaceId,
            String name,
            String rootPath,
            String physicalRootPath,
            String status,
            String linuxServerId,
            Instant createdAt,
            Instant updatedAt,
            String appId,
            String versionId,
            String applicationWorkspaceId) {

        /**
         * 兼容既有内部构造调用。仅供测试或内部适配构造完整对象；对外响应必须通过 {@link #from(Workspace)}
         * 生成逻辑工作区标识，并清空物理路径。
         */
        public WorkspaceRuntimeResponse(
                String workspaceId,
                String name,
                String rootPath,
                String status,
                String linuxServerId,
                Instant createdAt,
                Instant updatedAt,
                String appId,
                String versionId,
                String applicationWorkspaceId) {
            // rootPath 仅为旧调用签名占位，禁止把调用方传入的物理路径投影到响应。
            this(
                    workspaceId,
                    name,
                    "workspace:" + workspaceId,
                    null,
                    status,
                    linuxServerId,
                    createdAt,
                    updatedAt,
                    appId,
                    versionId,
                    applicationWorkspaceId);
        }

        public static WorkspaceRuntimeResponse from(Workspace workspace) {
            return from(workspace, null, null, null);
        }

        public static WorkspaceRuntimeResponse from(Workspace workspace, String appId) {
            return from(workspace, appId, null, null);
        }

        public static WorkspaceRuntimeResponse from(
                Workspace workspace, String appId, String versionId, String applicationWorkspaceId) {
            return new WorkspaceRuntimeResponse(
                    workspace.workspaceId().value(),
                    workspace.name(),
                    "workspace:" + workspace.workspaceId().value(),
                    null,
                    workspace.status().name(),
                    workspace.linuxServerId(),
                    workspace.createdAt(),
                    workspace.updatedAt(),
                    appId,
                    versionId,
                    applicationWorkspaceId);
        }
    }

    public record WorkspaceTemplateResponse(
            String workspaceId,
            String appId,
            String repositoryId,
            String directoryPath,
            String workspaceName,
            String branch,
            boolean enabled,
            boolean standard,
            String repositoryType,
            Instant createdAt,
            Instant updatedAt,
            AutomationActiveVersionResponse activeVersion) {
        public static WorkspaceTemplateResponse from(
                ApplicationWorkspace workspace,
                boolean standard,
                String repositoryType) {
            return from(workspace, standard, repositoryType, null);
        }

        public static WorkspaceTemplateResponse from(
                ApplicationWorkspace workspace,
                boolean standard,
                String repositoryType,
                AutomationActiveVersionResponse activeVersion) {
            return new WorkspaceTemplateResponse(
                    workspace.workspaceId().value(),
                    workspace.appId().value(),
                    workspace.repositoryId().value(),
                    workspace.directoryPath(),
                    workspace.workspaceName(),
                    workspace.branch(),
                    workspace.enabled(),
                    standard,
                    repositoryType,
                    workspace.createdAt(),
                    workspace.updatedAt(),
                    activeVersion);
        }
    }

    /** 自动化代码库配置当前激活的只读版本及本服务器副本状态。 */
    public record AutomationActiveVersionResponse(
            String versionId,
            String version,
            String branch,
            String targetCommitHash,
            String replicaStatus,
            String activatedByUserId,
            Instant activatedAt) {
    }

    /** 自动化只读版本的一轮多服务器同步投影；只返回逻辑指针和服务器状态，不暴露副本路径。 */
    public record AutomationVersionSynchronizationResponse(
            String applicationWorkspaceId,
            String workspaceName,
            String repositoryId,
            String repositoryName,
            String versionId,
            String version,
            String branch,
            String targetCommitHash,
            String status,
            String operation,
            int targetServerCount,
            int readyServerCount,
            List<AutomationVersionServerSynchronizationResponse> servers,
            String traceId,
            String message) {

        public AutomationVersionSynchronizationResponse {
            servers = servers == null ? List.of() : List.copyOf(servers);
        }
    }

    /** 自动化只读版本在单台在线服务器上的共享副本状态。 */
    public record AutomationVersionServerSynchronizationResponse(
            String linuxServerId,
            String serverName,
            String status,
            boolean online,
            String currentBranch,
            String currentCommitHash,
            Boolean matchesTarget,
            Instant syncedAt,
            String error) {
    }

    /**
     * 当前用户对应用版本所关联 Git 版本库的只读访问预检结果。
     * reason 仅在不可访问时返回稳定枚举，前端据此区分申请仓库权限和补充 SSH key。
     */
    public record GitRepositoryAccessResponse(
            boolean accessible,
            String repositoryId,
            String repositoryName,
            String branch,
            String reason) {
    }

    public record ApplicationWorkspaceCreateResponse(
            String workspaceId,
            String appId,
            String repositoryId,
            String branch,
            String directoryPath,
            String workspaceName,
            ApplicationWorkspaceVersionResponse initialVersion,
            Instant createdAt,
            Instant updatedAt) {
        public static ApplicationWorkspaceCreateResponse from(
                ApplicationWorkspace workspace,
                ApplicationWorkspaceVersionResponse initialVersion) {
            return new ApplicationWorkspaceCreateResponse(
                    workspace.workspaceId().value(),
                    workspace.appId().value(),
                    workspace.repositoryId().value(),
                    workspace.branch(),
                    workspace.directoryPath(),
                    workspace.workspaceName(),
                    initialVersion,
                    workspace.createdAt(),
                    workspace.updatedAt());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApplicationWorkspaceVersionResponse(
            String versionId,
            String applicationWorkspaceId,
            String appId,
            String repositoryId,
            String version,
            String branch,
            String repoRootPath,
            String workspaceRootPath,
            WorkspaceRuntimeResponse runtimeWorkspace,
            String status,
            String targetCommitHash,
            String replicaCommitHash,
            String replicaLinuxServerId,
            String replicaStatus,
            Instant createdAt,
            Instant updatedAt) {

        public ApplicationWorkspaceVersionResponse(
                String versionId,
                String applicationWorkspaceId,
                String appId,
                String repositoryId,
                String version,
                String branch,
                String repoRootPath,
                String workspaceRootPath,
                WorkspaceRuntimeResponse runtimeWorkspace,
                String status,
                Instant createdAt,
                Instant updatedAt) {
            this(
                    versionId,
                    applicationWorkspaceId,
                    appId,
                    repositoryId,
                    version,
                    branch,
                    repoRootPath,
                    workspaceRootPath,
                    runtimeWorkspace,
                    status,
                    null,
                    null,
                    null,
                    null,
                    createdAt,
                    updatedAt);
        }

        public static ApplicationWorkspaceVersionResponse from(ApplicationWorkspaceVersion version, Workspace workspace) {
            return from(version, null, workspace);
        }

        public static ApplicationWorkspaceVersionResponse from(
                ApplicationWorkspaceVersion version,
                ApplicationWorkspaceVersionReplica replica,
                Workspace workspace) {
            return new ApplicationWorkspaceVersionResponse(
                    version.versionId().value(),
                    version.applicationWorkspaceId().value(),
                    version.appId().value(),
                    version.repositoryId().value(),
                    version.version(),
                    version.branch(),
                    version.repoRootPath(),
                    version.workspaceRootPath(),
                    WorkspaceRuntimeResponse.from(workspace),
                    version.status().name(),
                    version.targetCommitHash(),
                    replica == null ? null : replica.currentCommitHash(),
                    replica == null ? null : replica.linuxServerId(),
                    replica == null ? null : replica.syncStatus().name(),
                    version.createdAt(),
                    version.updatedAt());
        }

        /** 自动化引用版本只返回逻辑版本与副本状态，禁止序列化任何服务器物理路径。 */
        public static ApplicationWorkspaceVersionResponse readonlyReference(
                ApplicationWorkspaceVersion version,
                ApplicationWorkspaceVersionReplica replica) {
            return new ApplicationWorkspaceVersionResponse(
                    version.versionId().value(),
                    version.applicationWorkspaceId().value(),
                    version.appId().value(),
                    version.repositoryId().value(),
                    version.version(),
                    version.branch(),
                    null,
                    null,
                    null,
                    version.status().name(),
                    version.targetCommitHash(),
                    replica == null ? null : replica.currentCommitHash(),
                    replica == null ? null : replica.linuxServerId(),
                    replica == null ? null : replica.syncStatus().name(),
                    version.createdAt(),
                    version.updatedAt());
        }
    }

    public record PersonalWorkspaceResponse(
            String personalWorkspaceId,
            String versionId,
            String appId,
            String applicationWorkspaceId,
            String workspaceName,
            String branch,
            String repoRootPath,
            String workspaceRootPath,
            WorkspaceRuntimeResponse runtimeWorkspace,
            String baseCommit,
            String status,
            Instant createdAt,
            Instant updatedAt) {
        public static PersonalWorkspaceResponse from(PersonalWorkspace workspace, Workspace runtimeWorkspace) {
            return new PersonalWorkspaceResponse(
                    workspace.personalWorkspaceId().value(),
                    workspace.versionId().value(),
                    workspace.appId().value(),
                    workspace.applicationWorkspaceId().value(),
                    workspace.workspaceName(),
                    workspace.branch(),
                    workspace.repoRootPath(),
                    workspace.workspaceRootPath(),
                    WorkspaceRuntimeResponse.from(runtimeWorkspace),
                    workspace.baseCommit(),
                    workspace.status().name(),
                    workspace.createdAt(),
                    workspace.updatedAt());
        }
    }

    /** 当前用户个人 worktree 拉取远端 feature 分支后的结果；不会更新应用共享版本目标。 */
    public record PersonalWorkspaceGitPullResponse(
            String personalWorkspaceId,
            String versionId,
            String remoteBranch,
            String commitHash,
            boolean updated,
            boolean agentConfigChanged,
            String runtimeReloadStatus,
            String runtimeReloadId,
            List<String> changedFiles) {
    }

    /** 超级管理员页面展示的应用 Git 刷新范围，明确列出全部工作空间版本和实际 feature 分支。 */
    public record ApplicationGitRefreshScopeResponse(
            String appId,
            String appName,
            boolean enabled,
            int totalGroups,
            List<ApplicationGitRefreshScopeGroupResponse> groups) {
    }

    /** 单个物理 feature 仓库组及其包含的应用工作空间。 */
    public record ApplicationGitRefreshScopeGroupResponse(
            String repositoryId,
            String repositoryName,
            String version,
            String branch,
            int workspaceCount,
            List<ApplicationGitRefreshScopeWorkspaceResponse> workspaces) {
    }

    /** 刷新范围中的工作空间目录视图；同一工作空间的不同版本可对应不同 feature 分支。 */
    public record ApplicationGitRefreshScopeWorkspaceResponse(
            String versionId,
            String applicationWorkspaceId,
            String workspaceName,
            String directoryPath,
            boolean enabled) {
    }

    /** 超级管理员按应用刷新全部 feature 仓库组后的汇总结果。 */
    public record ApplicationGitRefreshResponse(
            String appId,
            String appName,
            int totalGroups,
            int updatedGroups,
            int unchangedGroups,
            int failedGroups,
            List<ApplicationGitRefreshGroupResponse> groups) {
    }

    /** 单个“版本库 + 版本 + 分支”物理 feature 仓库组的刷新结果。 */
    public record ApplicationGitRefreshGroupResponse(
            String versionId,
            String repositoryId,
            String repositoryName,
            String version,
            String branch,
            int workspaceCount,
            String previousCommitHash,
            String commitHash,
            String status,
            String errorCode,
            String message) {
    }

    public record WorkspaceDiffFileResponse(String path, String status, boolean conflict) {
    }

    public record WorkspaceDiffResponse(List<WorkspaceDiffFileResponse> files) {
    }

    public record WorkspaceSyncResponse(String syncRecordId, String status, List<String> files, boolean force) {
    }

    public record WorkspaceCreateOperationStepResponse(
            String code,
            String name,
            String status) {
    }

    public record WorkspaceCreateOperationResponse(
            String operationId,
            String status,
            String currentStep,
            String errorCode,
            String errorMessage,
            String workspaceId,
            String versionId,
            List<WorkspaceCreateOperationStepResponse> steps,
            Instant createdAt,
            Instant updatedAt) {
        public static WorkspaceCreateOperationResponse from(WorkspaceCreateOperation operation) {
            return new WorkspaceCreateOperationResponse(
                    operation.operationId(),
                    operation.status().name(),
                    operation.currentStep().name(),
                    operation.errorCode(),
                    operation.errorMessage(),
                    operation.workspaceId() == null ? null : operation.workspaceId().value(),
                    operation.versionId() == null ? null : operation.versionId().value(),
                    steps(operation),
                    operation.createdAt(),
                    operation.updatedAt());
        }

        private static List<WorkspaceCreateOperationStepResponse> steps(WorkspaceCreateOperation operation) {
            List<WorkspaceCreateOperationStepResponse> result = new ArrayList<>();
            WorkspaceCreateOperationStep current = operation.currentStep();
            for (WorkspaceCreateOperationStep step : WorkspaceCreateOperationStep.values()) {
                result.add(new WorkspaceCreateOperationStepResponse(
                        step.name(),
                        step.displayName(),
                        stepStatus(operation.status(), current, step)));
            }
            return result;
        }

        private static String stepStatus(
                WorkspaceCreateOperationStatus operationStatus,
                WorkspaceCreateOperationStep current,
                WorkspaceCreateOperationStep step) {
            if (operationStatus == WorkspaceCreateOperationStatus.SUCCEEDED) {
                return "SUCCEEDED";
            }
            if (step.ordinal() < current.ordinal()) {
                return "SUCCEEDED";
            }
            if (step == current) {
                return operationStatus == WorkspaceCreateOperationStatus.FAILED ? "FAILED" : "RUNNING";
            }
            return "PENDING";
        }
    }

    /**
     * 用户最近 VCS 分支偏好响应；分支切换按钮持久化使用。
     */
    public record BranchPreferenceResponse(
            String appId,
            String workspaceId,
            String branch,
            Instant updatedAt) {
        public static BranchPreferenceResponse from(UserWorkspaceBranchPreference preference) {
            return new BranchPreferenceResponse(
                    preference.appId().value(),
                    preference.workspaceId().value(),
                    preference.branch(),
                    preference.updatedAt());
        }
    }

    /**
     * 工作空间创建已接受的响应（异步模式），前端通过 operationId 轮询进度。
     */
    public record CreateWorkspaceAcceptedResponse(
            String operationId,
            String status,  // "ACCEPTED"
            Instant createdAt) {
    }

    /**
     * 基于本地 Git status/diff 的工作区变更文件响应，不依赖 opencode runtime。
     * staged 标识是否已 git add；patch/additions/deletions 来自 git diff。
     */
    public record WorkspaceGitDiffFileResponse(
            String path,
            String rawStatus,
            String status,
            boolean staged,
            String patch,
            int additions,
            int deletions) {
    }

    /**
     * 个人 worktree 的 Git 变更和应用 feature 同步状态。
     *
     * <p>新增字段保持 JSON 向后兼容：mergeInProgress 表示 Git 已进入待解决/待提交的 merge，
     * applicationUpdatePending 表示当前个人 HEAD 尚未包含版本固定的 target commit；
     * applicationUpdateBlockingFiles 单独返回整个个人仓库的阻塞文件，避免当前目录 pathspec
     * 把同仓库其它工作空间的本地变更隐藏掉。</p>
     */
    public record WorkspaceGitDiffResponse(
            List<WorkspaceGitDiffFileResponse> files,
            boolean mergeInProgress,
            boolean applicationUpdatePending,
            String applicationTargetCommit,
            List<WorkspaceGitUpdateBlockerResponse> applicationUpdateBlockingFiles) {

        public WorkspaceGitDiffResponse(List<WorkspaceGitDiffFileResponse> files) {
            this(files, false, false, null, List.of());
        }
    }

    /** 应用更新被个人仓库本地变更阻塞时的仓库级文件定位。 */
    public record WorkspaceGitUpdateBlockerResponse(
            String path,
            String rawStatus,
            String applicationWorkspaceId,
            String workspaceName,
            String directoryPath) {
    }

    public record WorkspaceGitMergeCompletionResponse(
            String status,
            String headCommit,
            String applicationTargetCommit) {
    }

    /** 按运行态 Workspace 执行的本地提交；该回包不承载任何远程推送语义。 */
    public record WorkspaceGitCommitResponse(
            String status,
            String workspaceId,
            String headCommit,
            String message) {
    }

    /**
     * Git 三方冲突内容。content 为 null 表示对应版本中不存在该文件。
     */
    public record WorkspaceGitConflictResponse(
            String path,
            String rawStatus,
            String baseContent,
            String currentContent,
            String incomingContent,
            String resultContent) {
    }

    /**
     * 个人工作区本地提交或发布响应。
     * status 为 LOCAL_COMMITTED 表示仅更新个人 worktree；PUBLISHED 表示已从个人 HEAD
     * 按白名单投影到应用 feature 分支并推送；CONFLICT 时 conflictFiles 列出冲突文件路径。
     */
    public record PersonalWorkspacePublishResponse(
            String status,              // "LOCAL_COMMITTED" / "PUBLISHED" / "CONFLICT"
            String personalWorkspaceId,
            String versionId,
            List<String> conflictFiles,
            String message,
            boolean remotePushed,
            String headCommit,
            String remoteBranch,
            List<String> executedCommands,
            String currentStep) {

        public PersonalWorkspacePublishResponse(
                String status,
                String personalWorkspaceId,
                String versionId,
                List<String> conflictFiles,
                String message,
                boolean remotePushed,
                String headCommit) {
            this(status, personalWorkspaceId, versionId, conflictFiles, message, remotePushed, headCommit, null, List.of(), null);
        }

        public PersonalWorkspacePublishResponse(
                String status,
                String personalWorkspaceId,
                String versionId,
                List<String> conflictFiles,
                String message,
                boolean remotePushed,
                String headCommit,
                String remoteBranch) {
            this(status, personalWorkspaceId, versionId, conflictFiles, message, remotePushed, headCommit, remoteBranch, List.of(), null);
        }

        public PersonalWorkspacePublishResponse withExecution(List<String> executedCommands, String currentStep) {
            return new PersonalWorkspacePublishResponse(
                    status,
                    personalWorkspaceId,
                    versionId,
                    conflictFiles,
                    message,
                    remotePushed,
                    headCommit,
                    remoteBranch,
                    executedCommands,
                    currentStep);
        }
    }

    /**
     * 发布前只同步应用分支并汇总待合入个人分支的变化，不修改个人 worktree。
     */
    public record PersonalWorkspacePublishPreviewResponse(
            String applicationHead,
            String personalHead,
            int incomingCommitCount,
            int changedFileCount,
            int addedCount,
            int modifiedCount,
            int deletedCount,
            int renamedCount,
            List<String> samplePaths) {
    }

    /**
     * 确保默认私有工作区存在后的统一响应，同时返回个人工作区和运行态 workspace。
     */
    public record DefaultPersonalWorkspaceResponse(
            String personalWorkspaceId,
            String personalWorkspaceName,
            String personalWorkspaceBranch,
            WorkspaceRuntimeResponse runtimeWorkspace) {
    }
}
