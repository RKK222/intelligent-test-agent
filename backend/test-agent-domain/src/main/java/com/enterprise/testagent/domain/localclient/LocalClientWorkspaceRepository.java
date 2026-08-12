package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;

/** 本地工作区绑定仓储端口。 */
public interface LocalClientWorkspaceRepository {

    Optional<LocalClientWorkspaceBinding> findByWorkspaceId(WorkspaceId workspaceId);

    List<LocalClientWorkspaceBinding> findByClientInstanceId(LocalClientInstanceId clientInstanceId);

    void save(LocalClientWorkspaceBinding binding);

    boolean deleteByWorkspaceId(WorkspaceId workspaceId);
}
