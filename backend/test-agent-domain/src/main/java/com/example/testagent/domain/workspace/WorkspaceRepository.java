package com.example.testagent.domain.workspace;

import com.example.testagent.common.pagination.PageRequest;
import com.example.testagent.common.pagination.PageResponse;
import java.util.Optional;

/**
 * Workspace 持久化端口，domain 只定义业务边界，具体 JDBC 实现位于 persistence 模块。
 */
public interface WorkspaceRepository {

    Workspace save(Workspace workspace);

    Optional<Workspace> findById(WorkspaceId workspaceId);

    PageResponse<Workspace> findPage(PageRequest pageRequest);
}
