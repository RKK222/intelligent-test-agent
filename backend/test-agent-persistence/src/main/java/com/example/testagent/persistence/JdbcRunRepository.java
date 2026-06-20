package com.example.testagent.persistence;

import com.example.testagent.domain.run.Run;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.run.RunRepository;
import com.example.testagent.domain.run.RunStatus;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.workspace.WorkspaceId;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Run JDBC Repository，只负责保存状态机结果，不在持久化层重新判断状态迁移。
 */
@Repository
public class JdbcRunRepository extends JdbcRepositorySupport implements RunRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<Run> rowMapper = (rs, rowNum) -> new Run(
            new RunId(rs.getString("run_id")),
            new SessionId(rs.getString("session_id")),
            new WorkspaceId(rs.getString("workspace_id")),
            RunStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"));

    /**
     * 注入 JdbcClient，持久化层只负责 Run 表字段映射。
     */
    public JdbcRunRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 保存运行状态快照；状态迁移合法性由领域和应用层保证。
     */
    @Override
    public Run save(Run run) {
        if (findById(run.runId()).isPresent()) {
            jdbcClient.sql("""
                            update runs
                            set session_id = :sessionId, workspace_id = :workspaceId, status = :status,
                                trace_id = :traceId, created_at = :createdAt, updated_at = :updatedAt
                            where run_id = :runId
                            """)
                    .param("runId", run.runId().value())
                    .param("sessionId", run.sessionId().value())
                    .param("workspaceId", run.workspaceId().value())
                    .param("status", run.status().name())
                    .param("traceId", run.traceId())
                    .param("createdAt", timestamp(run.createdAt()))
                    .param("updatedAt", timestamp(run.updatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at)
                            values (:runId, :sessionId, :workspaceId, :status, :traceId, :createdAt, :updatedAt)
                            """)
                    .param("runId", run.runId().value())
                    .param("sessionId", run.sessionId().value())
                    .param("workspaceId", run.workspaceId().value())
                    .param("status", run.status().name())
                    .param("traceId", run.traceId())
                    .param("createdAt", timestamp(run.createdAt()))
                    .param("updatedAt", timestamp(run.updatedAt()))
                    .update();
        }
        return run;
    }

    /**
     * 按运行 ID 查询运行状态。
     */
    @Override
    public Optional<Run> findById(RunId runId) {
        return jdbcClient.sql("""
                        select run_id, session_id, workspace_id, status, trace_id, created_at, updated_at
                        from runs
                        where run_id = :runId
                        """)
                .param("runId", runId.value())
                .query(rowMapper)
                .optional();
    }
}
