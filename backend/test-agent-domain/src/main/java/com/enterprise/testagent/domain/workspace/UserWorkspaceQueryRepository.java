package com.enterprise.testagent.domain.workspace;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Optional;

/**
 * 用户关联工作区只读查询端口，范围为个人工作区或用户归因会话实际引用的工作区。
 */
public interface UserWorkspaceQueryRepository {

    PageResponse<Workspace> findUserWorkspaces(UserId userId, PageRequest pageRequest);

    Optional<Workspace> findUserWorkspace(UserId userId, WorkspaceId workspaceId);
}
