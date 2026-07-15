package com.icbc.testagent.api.web.platform;

import java.util.List;

/**
 * 应用版本工作区 HTTP 请求 DTO，入口层只承载参数，不包含业务规则。
 */
final class ManagedWorkspaceDtos {

    private ManagedWorkspaceDtos() {
    }

    record CreateVersionRequest(String version, String branch) {
    }

    record CreatePersonalWorkspaceRequest(String workspaceName) {
    }

    record SyncWorkspaceRequest(List<String> files, Boolean force) {
    }

    record WorkspaceGitFilesRequest(List<String> files) {
    }

    record ResolveWorkspaceGitConflictRequest(String path, String resolution, String content) {
    }

    record ResolveAllWorkspaceGitConflictsRequest(String resolution) {
    }

    /**
     * VCS 分支偏好写入请求体：当前分支名由前端从 IDE 或 vcs.status 拿到。
     */
    record BranchPreferenceRequest(String branch) {
    }

    /**
     * 个人 worktree 本地提交或发布请求体。files 使用工作区 Git diff 返回的相对路径；
     * 发布阶段只从个人 HEAD 投影这些文件，不合并个人分支。
     */
    record PublishPersonalWorkspaceRequest(
            String commitMessage,
            List<String> files,
            String expectedApplicationHead,
            String operationId) {
    }
}
