package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.UserWorkspaceQueryRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 用户关联工作区只读查询服务，为普通用户对象级鉴权和排查只读入口提供同一范围定义。
 */
@Service
public class UserWorkspaceQueryService {

    private final UserWorkspaceQueryRepository repository;

    public UserWorkspaceQueryService(UserWorkspaceQueryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public PageResponse<Workspace> listUserWorkspaces(UserId userId, PageRequest pageRequest) {
        return repository.findUserWorkspaces(userId, pageRequest);
    }

    public Workspace requireUserWorkspace(UserId userId, WorkspaceId workspaceId) {
        return repository.findUserWorkspace(userId, workspaceId)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Workspace 不存在",
                        Map.of("workspaceId", workspaceId.value())));
    }
}
