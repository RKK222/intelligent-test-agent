package com.icbc.testagent.domain.managedworkspace;

import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ApplicationWorkspaceId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;

/**
 * 托管工作区持久化端口，屏蔽版本、个人空间、最近使用和同步审计的 JDBC 实现。
 */
public interface ManagedWorkspaceRepository {

    List<ApplicationWorkspaceVersion> findVersions(ApplicationWorkspaceId applicationWorkspaceId);

    List<ApplicationWorkspaceVersion> findVersionsByApplication(ApplicationId appId);

    Optional<ApplicationWorkspaceVersion> findVersion(ApplicationWorkspaceVersionId versionId);

    Optional<ApplicationWorkspaceVersion> findVersionByTemplateAndVersion(ApplicationWorkspaceId applicationWorkspaceId, String version);

    ApplicationWorkspaceVersion saveVersion(ApplicationWorkspaceVersion version);

    List<PersonalWorkspace> findPersonalWorkspaces(ApplicationWorkspaceVersionId versionId, UserId userId);

    Optional<PersonalWorkspace> findPersonalWorkspace(PersonalWorkspaceId personalWorkspaceId);

    Optional<PersonalWorkspace> findPersonalWorkspaceByRuntimeWorkspace(WorkspaceId workspaceId);

    PersonalWorkspace savePersonalWorkspace(PersonalWorkspace workspace);

    Optional<ApplicationWorkspaceVersion> findVersionByRuntimeWorkspace(WorkspaceId workspaceId);

    void savePreference(UserWorkspacePreference preference);

    Optional<UserWorkspacePreference> findGlobalPreference(UserId userId);

    Optional<UserWorkspacePreference> findApplicationPreference(UserId userId, ApplicationId appId);

    void saveSyncRecord(WorkspaceSyncRecord record);
}
