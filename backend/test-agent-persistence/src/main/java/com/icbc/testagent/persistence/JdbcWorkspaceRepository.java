package com.icbc.testagent.persistence;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceRepository;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Workspace JDBC Repository，负责领域对象和 workspaces 表之间的字段映射。
 */
@Repository
public class JdbcWorkspaceRepository extends JdbcRepositorySupport implements WorkspaceRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcWorkspaceRepository.class);

    private final JdbcClient jdbcClient;
    private final RowMapper<Workspace> rowMapper = (rs, rowNum) -> {
        // 历史脏数据兜底：部分工作区 updated_at 早于 created_at（多出现在分布式批量写入或时钟回拨场景），
        // 直接传给领域对象会触发 "updatedAt must not be before createdAt" 异常，污染 listWorkspaces 响应。
        // 持久化映射层把 updatedAt 抬到 createdAt，并打印 WARN 提醒上游排障；写入侧由 SQL 修复脚本一次性回填。
        String workspaceId = rs.getString("workspace_id");
        Instant createdAt = instant(rs, "created_at");
        Instant updatedAt = instant(rs, "updated_at");
        Instant normalizedUpdatedAt = normalizeUpdatedAt(workspaceId, createdAt, updatedAt);
        return new Workspace(
                new WorkspaceId(workspaceId),
                rs.getString("name"),
                rs.getString("root_path"),
                WorkspaceStatus.valueOf(rs.getString("status")),
                createdAt,
                normalizedUpdatedAt,
                rs.getString("linux_server_id"),
                rs.getString("trace_id"));
    };

    /**
     * 注入 JdbcClient，Repository 不直接管理连接生命周期。
     */
    public JdbcWorkspaceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 把 updatedAt 归一化到不早于 createdAt；当发现历史脏数据时打印 WARN，保留原始值供排障。
     */
    private Instant normalizeUpdatedAt(String workspaceId, Instant createdAt, Instant updatedAt) {
        if (updatedAt == null || createdAt == null) {
            return updatedAt;
        }
        if (updatedAt.isBefore(createdAt)) {
            LOGGER.warn(
                    "Detected legacy workspace row with updatedAt before createdAt, clamping; workspaceId={}, createdAt={}, updatedAt={}",
                    workspaceId, createdAt, updatedAt);
            return createdAt;
        }
        return updatedAt;
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
                                linux_server_id = :linuxServerId, trace_id = :traceId,
                                created_at = :createdAt, updated_at = :updatedAt
                            where workspace_id = :workspaceId
                            """)
                    .param("workspaceId", workspace.workspaceId().value())
                    .param("name", workspace.name())
                    .param("rootPath", workspace.rootPath())
                    .param("status", workspace.status().name())
                    .param("linuxServerId", workspace.linuxServerId())
                    .param("traceId", workspace.traceId())
                    .param("createdAt", timestamp(workspace.createdAt()))
                    .param("updatedAt", timestamp(workspace.updatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into workspaces(workspace_id, name, root_path, status, linux_server_id, trace_id, created_at, updated_at)
                            values (:workspaceId, :name, :rootPath, :status, :linuxServerId, :traceId, :createdAt, :updatedAt)
                            """)
                    .param("workspaceId", workspace.workspaceId().value())
                    .param("name", workspace.name())
                    .param("rootPath", workspace.rootPath())
                    .param("status", workspace.status().name())
                    .param("linuxServerId", workspace.linuxServerId())
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
                        select workspace_id, name, root_path, status, linux_server_id, trace_id, created_at, updated_at
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
                        select workspace_id, name, root_path, status, linux_server_id, trace_id, created_at, updated_at
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
