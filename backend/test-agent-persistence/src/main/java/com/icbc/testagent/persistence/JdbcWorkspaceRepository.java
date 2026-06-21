package com.icbc.testagent.persistence;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Workspace JDBC Repository，负责领域对象和 workspaces 表之间的字段映射。
 */
@Repository
public class JdbcWorkspaceRepository extends JdbcRepositorySupport implements WorkspaceRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<Workspace> rowMapper = (rs, rowNum) -> new Workspace(
            new WorkspaceId(rs.getString("workspace_id")),
            rs.getString("name"),
            rs.getString("root_path"),
            WorkspaceStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"));

    /**
     * 注入 JdbcClient，Repository 不直接管理连接生命周期。
     */
    public JdbcWorkspaceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 保存工作区；存在时全量更新，缺失时插入新记录。
     */
    @Override
    public Workspace save(Workspace workspace) {
        if (findById(workspace.workspaceId()).isPresent()) {
            jdbcClient.sql("""
                            update workspaces
                            set name = :name, root_path = :rootPath, status = :status,
                                trace_id = :traceId, created_at = :createdAt, updated_at = :updatedAt
                            where workspace_id = :workspaceId
                            """)
                    .param("workspaceId", workspace.workspaceId().value())
                    .param("name", workspace.name())
                    .param("rootPath", workspace.rootPath())
                    .param("status", workspace.status().name())
                    .param("traceId", workspace.traceId())
                    .param("createdAt", timestamp(workspace.createdAt()))
                    .param("updatedAt", timestamp(workspace.updatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                            values (:workspaceId, :name, :rootPath, :status, :traceId, :createdAt, :updatedAt)
                            """)
                    .param("workspaceId", workspace.workspaceId().value())
                    .param("name", workspace.name())
                    .param("rootPath", workspace.rootPath())
                    .param("status", workspace.status().name())
                    .param("traceId", workspace.traceId())
                    .param("createdAt", timestamp(workspace.createdAt()))
                    .param("updatedAt", timestamp(workspace.updatedAt()))
                    .update();
        }
        return workspace;
    }

    /**
     * 按工作区 ID 查询单条记录。
     */
    @Override
    public Optional<Workspace> findById(WorkspaceId workspaceId) {
        return jdbcClient.sql("""
                        select workspace_id, name, root_path, status, trace_id, created_at, updated_at
                        from workspaces
                        where workspace_id = :workspaceId
                        """)
                .param("workspaceId", workspaceId.value())
                .query(rowMapper)
                .optional();
    }

    /**
     * 按创建时间倒序分页读取工作区，并返回总数供前端分页。
     */
    @Override
    public PageResponse<Workspace> findPage(PageRequest pageRequest) {
        var items = jdbcClient.sql("""
                        select workspace_id, name, root_path, status, trace_id, created_at, updated_at
                        from workspaces
                        order by created_at desc, id desc
                        limit :limit offset :offset
                        """)
                .param("limit", pageRequest.size())
                .param("offset", pageRequest.offset())
                .query(rowMapper)
                .list();
        Long total = jdbcClient.sql("select count(*) from workspaces")
                .query(Long.class)
                .single();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
    }
}
