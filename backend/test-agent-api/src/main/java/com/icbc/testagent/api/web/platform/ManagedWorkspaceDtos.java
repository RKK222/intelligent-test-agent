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

    /**
     * VCS 分支偏好写入请求体：当前分支名由前端从 IDE 或 vcs.status 拿到。
     */
    record BranchPreferenceRequest(String branch) {
    }

    /**
     * 个人工作区"提交并推送"（合并回应用版本分支）的请求体。
     * files 使用工作区 Git diff 返回的相对路径，只发布前端暂存的文件。
     */
    record PublishPersonalWorkspaceRequest(String commitMessage, List<String> files) {
    }
}
