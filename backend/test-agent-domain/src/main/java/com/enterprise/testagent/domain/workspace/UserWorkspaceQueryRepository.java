package com.enterprise.testagent.domain.workspace;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Optional;

/**
 * 用户关联工作区只读查询端口，范围为个人工作区或用户归因会话实际引用的工作区。
 */
public interface UserWorkspaceQueryRepository {

    /** 按名称或 Workspace ID 过滤用户关联工作区；空查询保持原分页语义。 */
    PageResponse<Workspace> findUserWorkspaces(UserId userId, String query, PageRequest pageRequest);

    default PageResponse<Workspace> findUserWorkspaces(UserId userId, PageRequest pageRequest) {
        return findUserWorkspaces(userId, null, pageRequest);
    }

    Optional<Workspace> findUserWorkspace(UserId userId, WorkspaceId workspaceId);
}
