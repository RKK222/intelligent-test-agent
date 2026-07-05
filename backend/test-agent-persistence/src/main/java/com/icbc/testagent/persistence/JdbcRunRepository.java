package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Run JDBC Repository，只负责保存状态机结果，不在持久化层重新判断状态迁移。
 */
public class JdbcRunRepository extends JdbcRepositorySupport implements RunRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<Run> rowMapper = (rs, rowNum) -> new Run(
            new RunId(rs.getString("run_id")),
            new SessionId(rs.getString("session_id")),
            new WorkspaceId(rs.getString("workspace_id")),
            RunStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"),
            tokenUsage(rs.getObject("tokens_input", Long.class),
                    rs.getObject("tokens_output", Long.class),
                    rs.getObject("tokens_reasoning", Long.class),
                    rs.getObject("tokens_cache_read", Long.class),
                    rs.getObject("tokens_cache_write", Long.class)),
            rs.getBigDecimal("cost_usd"),
            ConversationSourceType.valueOf(rs.getString("source_type")),
            rs.getString("source_ref_id"),
            userId(rs.getString("triggered_by_user_id")),
            rs.getString("agent_id"),
            rs.getString("model_id"));

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
                                trace_id = :traceId, created_at = :createdAt, updated_at = :updatedAt,
                                tokens_input = :tokensInput, tokens_output = :tokensOutput,
                                tokens_reasoning = :tokensReasoning,
                                tokens_cache_read = :tokensCacheRead,
                                tokens_cache_write = :tokensCacheWrite,
                                cost_usd = :costUsd,
                                source_type = :sourceType,
                                source_ref_id = :sourceRefId,
                                triggered_by_user_id = :triggeredByUserId,
                                agent_id = :agentId,
                                model_id = :modelId
                            where run_id = :runId
                            """)
                    .param("runId", run.runId().value())
                    .param("sessionId", run.sessionId().value())
                    .param("workspaceId", run.workspaceId().value())
                    .param("status", run.status().name())
                    .param("traceId", run.traceId())
                    .param("createdAt", timestamp(run.createdAt()))
                    .param("updatedAt", timestamp(run.updatedAt()))
                    .param("tokensInput", run.tokenUsage().input())
                    .param("tokensOutput", run.tokenUsage().output())
                    .param("tokensReasoning", run.tokenUsage().reasoning())
                    .param("tokensCacheRead", run.tokenUsage().cacheRead())
                    .param("tokensCacheWrite", run.tokenUsage().cacheWrite())
                    .param("costUsd", run.costUsd())
                    .param("sourceType", run.sourceType().name())
                    .param("sourceRefId", run.sourceRefId())
                    .param("triggeredByUserId", userIdValue(run.triggeredByUserId()))
                    .param("agentId", run.agentId())
                    .param("modelId", run.modelId())
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into runs(
                                run_id, session_id, workspace_id, status, trace_id, created_at, updated_at,
                                tokens_input, tokens_output, tokens_reasoning,
                                tokens_cache_read, tokens_cache_write, cost_usd,
                                source_type, source_ref_id, triggered_by_user_id, agent_id, model_id
                            )
                            values (
                                :runId, :sessionId, :workspaceId, :status, :traceId, :createdAt, :updatedAt,
                                :tokensInput, :tokensOutput, :tokensReasoning,
                                :tokensCacheRead, :tokensCacheWrite, :costUsd,
                                :sourceType, :sourceRefId, :triggeredByUserId, :agentId, :modelId
                            )
                            """)
                    .param("runId", run.runId().value())
                    .param("sessionId", run.sessionId().value())
                    .param("workspaceId", run.workspaceId().value())
                    .param("status", run.status().name())
                    .param("traceId", run.traceId())
                    .param("createdAt", timestamp(run.createdAt()))
                    .param("updatedAt", timestamp(run.updatedAt()))
                    .param("tokensInput", run.tokenUsage().input())
                    .param("tokensOutput", run.tokenUsage().output())
                    .param("tokensReasoning", run.tokenUsage().reasoning())
                    .param("tokensCacheRead", run.tokenUsage().cacheRead())
                    .param("tokensCacheWrite", run.tokenUsage().cacheWrite())
                    .param("costUsd", run.costUsd())
                    .param("sourceType", run.sourceType().name())
                    .param("sourceRefId", run.sourceRefId())
                    .param("triggeredByUserId", userIdValue(run.triggeredByUserId()))
                    .param("agentId", run.agentId())
                    .param("modelId", run.modelId())
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
                        select run_id, session_id, workspace_id, status, trace_id, created_at, updated_at,
                               tokens_input, tokens_output, tokens_reasoning,
                               tokens_cache_read, tokens_cache_write, cost_usd,
                               source_type, source_ref_id, triggered_by_user_id, agent_id, model_id
                        from runs
                        where run_id = :runId
                        """)
                .param("runId", runId.value())
                .query(rowMapper)
                .optional();
    }

    /**
     * 查询指定会话最近仍在执行的 Run，供前端刷新后恢复 SSE。
     */
    @Override
    public Optional<Run> findLatestActiveBySessionId(SessionId sessionId) {
        return jdbcClient.sql("""
                        select run_id, session_id, workspace_id, status, trace_id, created_at, updated_at,
                               tokens_input, tokens_output, tokens_reasoning,
                               tokens_cache_read, tokens_cache_write, cost_usd,
                               source_type, source_ref_id, triggered_by_user_id, agent_id, model_id
                        from runs
                        where session_id = :sessionId
                          and status in ('PENDING', 'RUNNING', 'CANCELLING')
                        order by updated_at desc, id desc
                        limit 1
                        """)
                .param("sessionId", sessionId.value())
                .query(rowMapper)
                .optional();
    }

    private static TokenUsage tokenUsage(Long input, Long output, Long reasoning, Long cacheRead, Long cacheWrite) {
        return new TokenUsage(input, output, reasoning, cacheRead, cacheWrite);
    }

    private static UserId userId(String value) {
        return value == null ? null : new UserId(value);
    }

    private static String userIdValue(UserId userId) {
        return userId == null ? null : userId.value();
    }
}
