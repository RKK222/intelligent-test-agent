package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;

/** 自动化共享只读副本目录；浏览器只能携带逻辑身份、相对路径和服务端历史标签租约。 */
public interface AutomationWorkspaceReferenceCatalog {

    Reference resolveGeneration(
            UserId userId,
            WorkspaceId hostWorkspaceId,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation);

    default Reference resolveGeneration(
            UserId userId,
            WorkspaceId hostWorkspaceId,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation,
            String readLeaseToken) {
        return resolveGeneration(userId, hostWorkspaceId, appId, repositoryId, generation);
    }

    /** 为当前代次生成绑定用户、主工作区和版本库的短期只读标签租约。 */
    default String issueReadLease(UserId userId, WorkspaceId hostWorkspaceId, Reference reference) {
        return null;
    }

    record Reference(
            ApplicationId applicationId,
            CodeRepositoryId repositoryId,
            long generation,
            String displayName,
            String repositoryName,
            String branch,
            String targetCommitHash,
            String workspaceRootPath,
            String directoryPath,
            String configurationPath,
            String description,
            boolean current,
            String readLeaseToken) {

        /** 兼容不需要历史标签租约的既有调用方。 */
        public Reference(
                ApplicationId applicationId,
                CodeRepositoryId repositoryId,
                long generation,
                String displayName,
                String repositoryName,
                String branch,
                String targetCommitHash,
                String workspaceRootPath,
                String directoryPath,
                String configurationPath,
                String description,
                boolean current) {
            this(
                    applicationId, repositoryId, generation, displayName, repositoryName, branch,
                    targetCommitHash, workspaceRootPath, directoryPath, configurationPath, description,
                    current, null);
        }

        public Reference withDisplayName(String configuredDisplayName) {
            return new Reference(
                    applicationId,
                    repositoryId,
                    generation,
                    configuredDisplayName,
                    repositoryName,
                    branch,
                    targetCommitHash,
                    workspaceRootPath,
                    directoryPath,
                    configurationPath,
                    description,
                    current,
                    readLeaseToken);
        }

        public Reference withReadLease(String token) {
            return new Reference(
                    applicationId,
                    repositoryId,
                    generation,
                    displayName,
                    repositoryName,
                    branch,
                    targetCommitHash,
                    workspaceRootPath,
                    directoryPath,
                    configurationPath,
                    description,
                    current,
                    token);
        }
    }
}
