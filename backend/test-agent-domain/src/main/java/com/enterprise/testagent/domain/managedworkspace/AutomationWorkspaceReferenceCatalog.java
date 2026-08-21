package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;

/** 自动化共享只读副本目录；浏览器只能携带应用、版本库、代次和逻辑相对路径。 */
public interface AutomationWorkspaceReferenceCatalog {

    Reference resolveGeneration(
            UserId userId,
            WorkspaceId hostWorkspaceId,
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            long generation);

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
            boolean current) {

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
                    current);
        }
    }
}
