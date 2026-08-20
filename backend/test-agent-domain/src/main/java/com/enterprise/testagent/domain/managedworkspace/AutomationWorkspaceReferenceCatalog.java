package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.List;

/**
 * 自动化代码库只读引用目录。
 *
 * <p>物理根只允许在后端内部消费；浏览器协议必须继续使用配置、版本和相对路径组成的逻辑定位器。
 */
public interface AutomationWorkspaceReferenceCatalog {

    Resolution resolveActive(UserId userId, WorkspaceId hostWorkspaceId);

    Reference resolveVersion(
            UserId userId,
            WorkspaceId hostWorkspaceId,
            ApplicationWorkspaceId applicationWorkspaceId,
            ApplicationWorkspaceVersionId versionId);

    record Resolution(
            ApplicationId applicationId,
            int configuredCount,
            List<Reference> references,
            List<Warning> warnings) {

        public Resolution {
            references = references == null ? List.of() : List.copyOf(references);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public static Resolution empty() {
            return new Resolution(null, 0, List.of(), List.of());
        }
    }

    record Reference(
            ApplicationId applicationId,
            ApplicationWorkspaceId applicationWorkspaceId,
            ApplicationWorkspaceVersionId versionId,
            String workspaceName,
            String displayName,
            String repositoryName,
            String version,
            String branch,
            String targetCommitHash,
            String workspaceRootPath,
            String directoryPath,
            String configurationPath) {

        public Reference withDisplayName(String configuredDisplayName) {
            return new Reference(
                    applicationId,
                    applicationWorkspaceId,
                    versionId,
                    workspaceName,
                    configuredDisplayName,
                    repositoryName,
                    version,
                    branch,
                    targetCommitHash,
                    workspaceRootPath,
                    directoryPath,
                    configurationPath);
        }
    }

    record Warning(
            ApplicationWorkspaceId applicationWorkspaceId,
            String alias,
            String code,
            String message) {
    }
}
