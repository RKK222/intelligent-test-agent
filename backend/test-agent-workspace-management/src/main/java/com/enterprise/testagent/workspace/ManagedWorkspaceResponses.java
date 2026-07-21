package com.enterprise.testagent.workspace;

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
            String status,
            String linuxServerId,
            Instant createdAt,
            Instant updatedAt,
            String appId,
            String versionId,
            String applicationWorkspaceId) {
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
                    workspace.rootPath(),
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
            boolean standard,
            Instant createdAt,
            Instant updatedAt) {
        public static WorkspaceTemplateResponse from(ApplicationWorkspace workspace, boolean standard) {
            return new WorkspaceTemplateResponse(
                    workspace.workspaceId().value(),
                    workspace.appId().value(),
                    workspace.repositoryId().value(),
                    workspace.directoryPath(),
                    workspace.workspaceName(),
                    workspace.branch(),
                    standard,
                    workspace.createdAt(),
                    workspace.updatedAt());
        }
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
     * applicationUpdatePending 表示当前个人 HEAD 尚未包含版本固定的 target commit。</p>
     */
    public record WorkspaceGitDiffResponse(
            List<WorkspaceGitDiffFileResponse> files,
            boolean mergeInProgress,
            boolean applicationUpdatePending,
            String applicationTargetCommit) {

        public WorkspaceGitDiffResponse(List<WorkspaceGitDiffFileResponse> files) {
            this(files, false, false, null);
        }
    }

    public record WorkspaceGitMergeCompletionResponse(
            String status,
            String headCommit,
            String applicationTargetCommit) {
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
            this(status, personalWorkspaceId, versionId, conflictFiles, message, remotePushed, headCommit, List.of(), null);
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
