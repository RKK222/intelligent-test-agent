package com.icbc.testagent.workspace;

import com.icbc.testagent.domain.configuration.ApplicationDefinition;
import com.icbc.testagent.domain.configuration.ApplicationWorkspace;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspace;
import com.icbc.testagent.domain.managedworkspace.UserWorkspaceBranchPreference;
import com.icbc.testagent.domain.workspace.Workspace;
import java.time.Instant;
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

    public record WorkspaceRuntimeResponse(
            String workspaceId,
            String name,
            String rootPath,
            String status,
            String linuxServerId,
            Instant createdAt,
            Instant updatedAt) {
        public static WorkspaceRuntimeResponse from(Workspace workspace) {
            return new WorkspaceRuntimeResponse(
                    workspace.workspaceId().value(),
                    workspace.name(),
                    workspace.rootPath(),
                    workspace.status().name(),
                    workspace.linuxServerId(),
                    workspace.createdAt(),
                    workspace.updatedAt());
        }
    }

    public record WorkspaceTemplateResponse(
            String workspaceId,
            String appId,
            String repositoryId,
            String directoryPath,
            String workspaceName,
            String branch,
            Instant createdAt,
            Instant updatedAt) {
        public static WorkspaceTemplateResponse from(ApplicationWorkspace workspace) {
            return new WorkspaceTemplateResponse(
                    workspace.workspaceId().value(),
                    workspace.appId().value(),
                    workspace.repositoryId().value(),
                    workspace.directoryPath(),
                    workspace.workspaceName(),
                    workspace.branch(),
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
            Instant createdAt,
            Instant updatedAt) {
        public static ApplicationWorkspaceVersionResponse from(ApplicationWorkspaceVersion version, Workspace workspace) {
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
}
