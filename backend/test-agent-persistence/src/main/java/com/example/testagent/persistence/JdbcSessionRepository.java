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
            executionNodeId(rs.getString("opencode_execution_node_id")));

    public JdbcSessionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Session save(Session session) {
        if (findById(session.sessionId()).isPresent()) {
            jdbcClient.sql("""
                            update sessions
                            set workspace_id = :workspaceId, title = :title, status = :status,
                                trace_id = :traceId, created_at = :createdAt, updated_at = :updatedAt,
                                opencode_session_id = :opencodeSessionId,
                                opencode_execution_node_id = :opencodeExecutionNodeId
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
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into sessions(
                                session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                                opencode_session_id, opencode_execution_node_id
                            )
                            values (
                                :sessionId, :workspaceId, :title, :status, :traceId, :createdAt, :updatedAt,
                                :opencodeSessionId, :opencodeExecutionNodeId
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
                    .update();
        }
        return session;
    }

    @Override
    public Optional<Session> findById(SessionId sessionId) {
        return jdbcClient.sql("""
                        select session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                               opencode_session_id, opencode_execution_node_id
                        from sessions
                        where session_id = :sessionId
                        """)
                .param("sessionId", sessionId.value())
                .query(rowMapper)
                .optional();
    }

    @Override
    public PageResponse<Session> findByWorkspaceId(WorkspaceId workspaceId, PageRequest pageRequest) {
        var items = jdbcClient.sql("""
                        select session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                               opencode_session_id, opencode_execution_node_id
                        from sessions
                        where workspace_id = :workspaceId
                        order by created_at desc, id desc
                        limit :limit offset :offset
                        """)
                .param("workspaceId", workspaceId.value())
                .param("limit", pageRequest.size())
                .param("offset", pageRequest.offset())
                .query(rowMapper)
                .list();
        Long total = jdbcClient.sql("""
                        select count(*)
                        from sessions
                        where workspace_id = :workspaceId
                        """)
                .param("workspaceId", workspaceId.value())
                .query(Long.class)
                .single();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
    }

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

    private ExecutionNodeId executionNodeId(String value) {
        return value == null ? null : new ExecutionNodeId(value);
    }

    private String executionNodeIdValue(ExecutionNodeId executionNodeId) {
        return executionNodeId == null ? null : executionNodeId.value();
    }
}
