package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;

/** 本地工作区绑定仓储端口。 */
public interface LocalClientWorkspaceRepository {

    Optional<LocalClientWorkspaceBinding> findByWorkspaceId(WorkspaceId workspaceId);

    Optional<LocalClientWorkspaceBinding> findByOwnerClientAndRootDigest(
            UserId userId,
            LocalClientInstanceId clientInstanceId,
            String rootDigest);

    List<LocalClientWorkspaceBinding> findByClientInstanceId(LocalClientInstanceId clientInstanceId);

    /** 串行化同一客户端的根目录注册，防止并发请求绕过摘要查重后同时插入。 */
    void lockRegistration(UserId userId, LocalClientInstanceId clientInstanceId);

    void save(LocalClientWorkspaceBinding binding);

    boolean deleteByWorkspaceId(WorkspaceId workspaceId);
}
