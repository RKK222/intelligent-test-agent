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

    /** 跨客户端查找同一用户、同一真实路径和同一文件系统身份的历史绑定。 */
    List<LocalClientWorkspaceBinding> findByOwnerRootIdentity(
            UserId userId,
            String rootDigest,
            String fileSystemIdentity);

    List<LocalClientWorkspaceBinding> findByClientInstanceId(LocalClientInstanceId clientInstanceId);

    /** 串行化同一用户的根目录注册，防止新旧客户端并发接管同一历史绑定。 */
    void lockRegistration(UserId userId, LocalClientInstanceId clientInstanceId);

    void save(LocalClientWorkspaceBinding binding);

    /** 以旧客户端 ID 为 CAS 条件保留 workspaceId 并切换客户端绑定。 */
    boolean rebind(
            LocalClientWorkspaceBinding binding,
            LocalClientInstanceId expectedClientInstanceId);

    boolean deleteByWorkspaceId(WorkspaceId workspaceId);
}
