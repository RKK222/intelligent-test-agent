package com.example.testagent.persistence;

import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.session.SessionRepository;
import com.example.testagent.domain.session.SessionStatus;
import com.example.testagent.domain.workspace.WorkspaceId;
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
            rs.getString("trace_id"));

    public JdbcSessionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Session save(Session session) {
        if (findById(session.sessionId()).isPresent()) {
            jdbcClient.sql("""
                            update sessions
                            set workspace_id = :workspaceId, title = :title, status = :status,
                                trace_id = :traceId, created_at = :createdAt, updated_at = :updatedAt
                            where session_id = :sessionId
                            """)
                    .param("sessionId", session.sessionId().value())
                    .param("workspaceId", session.workspaceId().value())
                    .param("title", session.title())
                    .param("status", session.status().name())
                    .param("traceId", session.traceId())
                    .param("createdAt", session.createdAt())
                    .param("updatedAt", session.updatedAt())
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at)
                            values (:sessionId, :workspaceId, :title, :status, :traceId, :createdAt, :updatedAt)
                            """)
                    .param("sessionId", session.sessionId().value())
                    .param("workspaceId", session.workspaceId().value())
                    .param("title", session.title())
                    .param("status", session.status().name())
                    .param("traceId", session.traceId())
                    .param("createdAt", session.createdAt())
                    .param("updatedAt", session.updatedAt())
                    .update();
        }
        return session;
    }

    @Override
    public Optional<Session> findById(SessionId sessionId) {
        return jdbcClient.sql("""
                        select session_id, workspace_id, title, status, trace_id, created_at, updated_at
                        from sessions
                        where session_id = :sessionId
                        """)
                .param("sessionId", sessionId.value())
                .query(rowMapper)
                .optional();
    }
}
