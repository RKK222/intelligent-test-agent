package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Agent 会话绑定 JDBC Repository，作为新运行链路的远端会话主数据源。
 */
@Repository
public class JdbcAgentSessionBindingRepository extends JdbcRepositorySupport implements AgentSessionBindingRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<AgentSessionBinding> rowMapper = (rs, rowNum) -> new AgentSessionBinding(
            new SessionId(rs.getString("session_id")),
            rs.getString("agent_id"),
            rs.getString("remote_session_id"),
            new ExecutionNodeId(rs.getString("execution_node_id")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"));

    /**
     * 注入 JdbcClient，SQL 在本 Repository 内显式声明。
     */
    public JdbcAgentSessionBindingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 保存或更新绑定。使用查询后写入方式，保持与现有 H2/PostgreSQL 测试兼容。
     */
    @Override
    public AgentSessionBinding save(AgentSessionBinding binding) {
        String normalizedAgentId = normalizeAgentId(binding.agentId());
        if (findBySessionIdAndAgentId(binding.sessionId(), normalizedAgentId).isPresent()) {
            jdbcClient.sql("""
                            update agent_session_bindings
                            set remote_session_id = :remoteSessionId,
                                execution_node_id = :executionNodeId,
                                updated_at = :updatedAt,
                                trace_id = :traceId
                            where session_id = :sessionId
                              and agent_id = :agentId
                            """)
                    .param("sessionId", binding.sessionId().value())
                    .param("agentId", normalizedAgentId)
                    .param("remoteSessionId", binding.remoteSessionId())
                    .param("executionNodeId", binding.executionNodeId().value())
                    .param("updatedAt", timestamp(binding.updatedAt()))
                    .param("traceId", binding.traceId())
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into agent_session_bindings(
                                session_id, agent_id, remote_session_id, execution_node_id,
                                created_at, updated_at, trace_id
                            )
                            values (
                                :sessionId, :agentId, :remoteSessionId, :executionNodeId,
                                :createdAt, :updatedAt, :traceId
                            )
                            """)
                    .param("sessionId", binding.sessionId().value())
                    .param("agentId", normalizedAgentId)
                    .param("remoteSessionId", binding.remoteSessionId())
                    .param("executionNodeId", binding.executionNodeId().value())
                    .param("createdAt", timestamp(binding.createdAt()))
                    .param("updatedAt", timestamp(binding.updatedAt()))
                    .param("traceId", binding.traceId())
                    .update();
        }
        return new AgentSessionBinding(
                binding.sessionId(),
                normalizedAgentId,
                binding.remoteSessionId(),
                binding.executionNodeId(),
                binding.createdAt(),
                binding.updatedAt(),
                binding.traceId());
    }

    /**
     * 按平台 Session 与 agent 标志查询远端会话绑定。
     */
    @Override
    public Optional<AgentSessionBinding> findBySessionIdAndAgentId(SessionId sessionId, String agentId) {
        return jdbcClient.sql("""
                        select session_id, agent_id, remote_session_id, execution_node_id,
                               created_at, updated_at, trace_id
                        from agent_session_bindings
                        where session_id = :sessionId
                          and agent_id = :agentId
                        """)
                .param("sessionId", sessionId.value())
                .param("agentId", normalizeAgentId(agentId))
                .query(rowMapper)
                .optional();
    }

    /**
     * 按 agent 远端会话反查平台绑定，后续远端回调或事件入口可复用。
     */
    @Override
    public Optional<AgentSessionBinding> findByAgentIdAndRemoteSessionId(String agentId, String remoteSessionId) {
        return jdbcClient.sql("""
                        select session_id, agent_id, remote_session_id, execution_node_id,
                               created_at, updated_at, trace_id
                        from agent_session_bindings
                        where agent_id = :agentId
                          and remote_session_id = :remoteSessionId
                        """)
                .param("agentId", normalizeAgentId(agentId))
                .param("remoteSessionId", DomainValidation.requireText(remoteSessionId, "remoteSessionId"))
                .query(rowMapper)
                .optional();
    }

    /**
     * agentId 与 URL 标志保持一致，统一小写并去除首尾空白。
     */
    private String normalizeAgentId(String agentId) {
        return DomainValidation.requireText(agentId, "agentId").trim().toLowerCase(Locale.ROOT);
    }
}
