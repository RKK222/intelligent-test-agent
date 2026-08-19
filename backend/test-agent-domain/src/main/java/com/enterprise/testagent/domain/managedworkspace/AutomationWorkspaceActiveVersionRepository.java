package com.enterprise.testagent.domain.managedworkspace;

import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import java.util.List;
import java.util.Optional;

/** 自动化代码库只读版本激活状态的关系型持久化端口。 */
public interface AutomationWorkspaceActiveVersionRepository {

    Optional<AutomationWorkspaceActiveVersion> find(ApplicationWorkspaceId applicationWorkspaceId);

    List<AutomationWorkspaceActiveVersion> findByApplicationWorkspaceIds(
            List<ApplicationWorkspaceId> applicationWorkspaceIds);

    AutomationWorkspaceActiveVersion activate(AutomationWorkspaceActiveVersion activeVersion);

    AutomationWorkspaceActiveVersion initializeIfAbsent(AutomationWorkspaceActiveVersion activeVersion);
}
