package com.icbc.testagent.domain.workspace;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import java.util.Optional;

/**
 * Workspace 持久化端口，domain 只定义业务边界，具体 JDBC 实现位于 persistence 模块。
 */
public interface WorkspaceRepository {

    /**
     * 保存工作区聚合。
     */
    Workspace save(Workspace workspace);

    /**
     * 按工作区 ID 查询工作区。
     */
    Optional<Workspace> findById(WorkspaceId workspaceId);

    /**
     * 分页查询工作区列表。
     */
    PageResponse<Workspace> findPage(PageRequest pageRequest);
}
