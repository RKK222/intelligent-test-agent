package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.routing.RoutingDecision;
import com.icbc.testagent.domain.routing.RoutingDecisionRepository;
import com.icbc.testagent.domain.routing.RoutingReason;
import com.icbc.testagent.domain.run.RunId;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * RoutingDecision JDBC Repository，用于审计 Run 被派发到哪个执行节点。
 */
@Repository
public class JdbcRoutingDecisionRepository extends JdbcRepositorySupport implements RoutingDecisionRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<RoutingDecision> rowMapper = (rs, rowNum) -> new RoutingDecision(
            new RunId(rs.getString("run_id")),
            new ExecutionNodeId(rs.getString("execution_node_id")),
            RoutingReason.valueOf(rs.getString("reason")),
            instant(rs, "decided_at"),
            rs.getString("trace_id"));

    /**
     * 注入 JdbcClient，路由决策按 runId 做一对一审计记录。
     */
    public JdbcRoutingDecisionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 保存路由决策；同一 runId 再次保存时更新审计记录。
     */
    @Override
    public RoutingDecision save(RoutingDecision routingDecision) {
        if (findByRunId(routingDecision.runId()).isPresent()) {
            jdbcClient.sql("""
                            update routing_decisions
                            set execution_node_id = :executionNodeId, reason = :reason,
                                decided_at = :decidedAt, trace_id = :traceId
                            where run_id = :runId
                            """)
                    .param("runId", routingDecision.runId().value())
                    .param("executionNodeId", routingDecision.executionNodeId().value())
                    .param("reason", routingDecision.reason().name())
                    .param("decidedAt", timestamp(routingDecision.decidedAt()))
                    .param("traceId", routingDecision.traceId())
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into routing_decisions(run_id, execution_node_id, reason, decided_at, trace_id)
                            values (:runId, :executionNodeId, :reason, :decidedAt, :traceId)
                            """)
                    .param("runId", routingDecision.runId().value())
                    .param("executionNodeId", routingDecision.executionNodeId().value())
                    .param("reason", routingDecision.reason().name())
                    .param("decidedAt", timestamp(routingDecision.decidedAt()))
                    .param("traceId", routingDecision.traceId())
                    .update();
        }
        return routingDecision;
    }

    /**
     * 按运行 ID 查询路由决策。
     */
    @Override
    public Optional<RoutingDecision> findByRunId(RunId runId) {
        return jdbcClient.sql("""
                        select run_id, execution_node_id, reason, decided_at, trace_id
                        from routing_decisions
                        where run_id = :runId
                        """)
                .param("runId", runId.value())
                .query(rowMapper)
                .optional();
    }
}
