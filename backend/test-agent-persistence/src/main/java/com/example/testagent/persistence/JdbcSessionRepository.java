package com.example.testagent.persistence;

import com.example.testagent.common.pagination.PageRequest;
import com.example.testagent.common.pagination.PageResponse;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.session.SessionRepository;
import com.example.testagent.domain.session.SessionStatus;
import com.example.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Session JDBC Repository，保持会话持久化字段与领域模型显式映射。
 */
@Repository
public class JdbcSessionRepository extends JdbcRepositorySupport implements SessionRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<Session> rowMapper = (rs, rowNum) -> new Session(
            new SessionId(rs.getString("session_id")),
            new WorkspaceId(rs.getString("workspace_id")),
            rs.getString("title"),
            SessionStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"),
            rs.getString("opencode_session_id"),
            executionNodeId(rs.getString("opencode_execution_node_id")),
            rs.getBoolean("pinned"));

    /**
     * 注入 JdbcClient，所有 SQL 都在本 Repository 内显式声明。
     */
    public JdbcSessionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 保存会话；远端 opencode 映射和置顶状态随领域对象一起持久化。
     */
    @Override
    public Session save(Session session) {
        if (findById(session.sessionId()).isPresent()) {
            jdbcClient.sql("""
                            update sessions
                            set workspace_id = :workspaceId, title = :title, status = :status,
                                trace_id = :traceId, created_at = :createdAt, updated_at = :updatedAt,
                                opencode_session_id = :opencodeSessionId,
                                opencode_execution_node_id = :opencodeExecutionNodeId,
                                pinned = :pinned
                            where session_id = :sessionId
                            """)
                    .param("sessionId", session.sessionId().value())
                    .param("workspaceId", session.workspaceId().value())
                    .param("title", session.title())
                    .param("status", session.status().name())
                    .param("traceId", session.traceId())
                    .param("createdAt", timestamp(session.createdAt()))
                    .param("updatedAt", timestamp(session.updatedAt()))
                    .param("opencodeSessionId", session.opencodeSessionId())
                    .param("opencodeExecutionNodeId", executionNodeIdValue(session.opencodeExecutionNodeId()))
                    .param("pinned", session.pinned())
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into sessions(
                                session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                                opencode_session_id, opencode_execution_node_id, pinned
                            )
                            values (
                                :sessionId, :workspaceId, :title, :status, :traceId, :createdAt, :updatedAt,
                                :opencodeSessionId, :opencodeExecutionNodeId, :pinned
                            )
                            """)
                    .param("sessionId", session.sessionId().value())
                    .param("workspaceId", session.workspaceId().value())
                    .param("title", session.title())
                    .param("status", session.status().name())
                    .param("traceId", session.traceId())
                    .param("createdAt", timestamp(session.createdAt()))
                    .param("updatedAt", timestamp(session.updatedAt()))
                    .param("opencodeSessionId", session.opencodeSessionId())
                    .param("opencodeExecutionNodeId", executionNodeIdValue(session.opencodeExecutionNodeId()))
                    .param("pinned", session.pinned())
                    .update();
        }
        return session;
    }

    /**
     * 按会话 ID 查询，会返回归档会话，是否隐藏由应用层决定。
     */
    @Override
    public Optional<Session> findById(SessionId sessionId) {
        return jdbcClient.sql("""
                        select session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                               opencode_session_id, opencode_execution_node_id, pinned
                        from sessions
                        where session_id = :sessionId
                        """)
                .param("sessionId", sessionId.value())
                .query(rowMapper)
                .optional();
    }

    /**
     * 全局搜索 ACTIVE 会话，按置顶、更新时间和自增 ID 稳定排序。
     */
    @Override
    public PageResponse<Session> findPage(String query, PageRequest pageRequest) {
        String pattern = searchPattern(query);
        String queryFilter = pattern == null ? "" : """
                          and (lower(title) like :queryPattern
                              or lower(session_id) like :queryPattern)
                        """;
        var itemsSpec = jdbcClient.sql("""
                        select session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                               opencode_session_id, opencode_execution_node_id, pinned
                        from sessions
                        where status = :status
                        """ + queryFilter + """
                        order by pinned desc, updated_at desc, id desc
                        limit :limit offset :offset
                        """)
                .param("status", SessionStatus.ACTIVE.name())
                .param("limit", pageRequest.size())
                .param("offset", pageRequest.offset());
        if (pattern != null) {
            itemsSpec = itemsSpec.param("queryPattern", pattern);
        }
        var items = itemsSpec.query(rowMapper)
                .list();
        var totalSpec = jdbcClient.sql("""
                        select count(*)
                        from sessions
                        where status = :status
                        """ + queryFilter)
                .param("status", SessionStatus.ACTIVE.name());
        if (pattern != null) {
            totalSpec = totalSpec.param("queryPattern", pattern);
        }
        Long total = totalSpec
                .query(Long.class)
                .single();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
    }

    /**
     * 查询指定工作区 ACTIVE 会话，归档会话默认不出现在列表中。
     */
    @Override
    public PageResponse<Session> findByWorkspaceId(WorkspaceId workspaceId, PageRequest pageRequest) {
        var items = jdbcClient.sql("""
                        select session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                               opencode_session_id, opencode_execution_node_id, pinned
                        from sessions
                        where workspace_id = :workspaceId
                          and status = :status
                        order by pinned desc, updated_at desc, id desc
                        limit :limit offset :offset
                        """)
                .param("workspaceId", workspaceId.value())
                .param("status", SessionStatus.ACTIVE.name())
                .param("limit", pageRequest.size())
                .param("offset", pageRequest.offset())
                .query(rowMapper)
                .list();
        Long total = jdbcClient.sql("""
                        select count(*)
                        from sessions
                        where workspace_id = :workspaceId
                          and status = :status
                        """)
                .param("workspaceId", workspaceId.value())
                .param("status", SessionStatus.ACTIVE.name())
                .query(Long.class)
                .single();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
    }

    /**
     * 绑定平台会话到远端 opencode 会话和执行节点，用于后续运行复用。
     */
    @Override
    public Optional<Session> attachOpencodeSession(
            SessionId sessionId,
            String opencodeSessionId,
            ExecutionNodeId executionNodeId,
            Instant updatedAt,
            String traceId) {
        jdbcClient.sql("""
                        update sessions
                        set opencode_session_id = :opencodeSessionId,
                            opencode_execution_node_id = :opencodeExecutionNodeId,
                            updated_at = :updatedAt,
                            trace_id = :traceId
                        where session_id = :sessionId
                        """)
                .param("sessionId", sessionId.value())
                .param("opencodeSessionId", opencodeSessionId)
                .param("opencodeExecutionNodeId", executionNodeId.value())
                .param("updatedAt", timestamp(updatedAt))
                .param("traceId", traceId)
                .update();
        return findById(sessionId);
    }

    /**
     * 将用户查询转换为大小写不敏感的 LIKE pattern，空查询表示不过滤。
     */
    private String searchPattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return "%" + query.toLowerCase() + "%";
    }

    /**
     * 将可空字符串转换为可空执行节点 ID。
     */
    private ExecutionNodeId executionNodeId(String value) {
        return value == null ? null : new ExecutionNodeId(value);
    }

    /**
     * 将可空执行节点 ID 转回 JDBC 参数。
     */
    private String executionNodeIdValue(ExecutionNodeId executionNodeId) {
        return executionNodeId == null ? null : executionNodeId.value();
    }
}
