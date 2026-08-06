package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.ManagedWorkspacePathResolver;
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
    private final ManagedWorkspacePathResolver pathResolver;

    public UserWorkspaceQueryService(
            UserWorkspaceQueryRepository repository,
            ManagedWorkspacePathResolver pathResolver) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver must not be null");
    }

    /** 用户范围过滤完成后仍解析托管逻辑路径，保持 Workspace API 返回物理绝对路径。 */
    public PageResponse<Workspace> listUserWorkspaces(UserId userId, PageRequest pageRequest) {
        PageResponse<Workspace> page = repository.findUserWorkspaces(userId, pageRequest);
        return new PageResponse<>(
                page.items().stream().map(pathResolver::withResolvedRootPath).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    /** 详情查询与列表使用同一物理路径响应语义，避免前端把 personalworktree 逻辑值传给外部页面。 */
    public Workspace requireUserWorkspace(UserId userId, WorkspaceId workspaceId) {
        return repository.findUserWorkspace(userId, workspaceId)
                .map(pathResolver::withResolvedRootPath)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "Workspace 不存在",
                        Map.of("workspaceId", workspaceId.value())));
    }
}
