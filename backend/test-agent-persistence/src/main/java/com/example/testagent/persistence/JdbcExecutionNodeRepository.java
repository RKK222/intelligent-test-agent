package com.example.testagent.persistence;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.node.ExecutionNodeRepository;
import com.example.testagent.domain.node.ExecutionNodeStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * ExecutionNode JDBC Repository，保存节点健康、容量和能力标签供路由读取。
 */
@Repository
public class JdbcExecutionNodeRepository extends JdbcRepositorySupport implements ExecutionNodeRepository {

    private static final TypeReference<Set<String>> CAPABILITIES_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RowMapper<ExecutionNode> rowMapper = (rs, rowNum) -> new ExecutionNode(
            new ExecutionNodeId(rs.getString("execution_node_id")),
            rs.getString("base_url"),
            ExecutionNodeStatus.valueOf(rs.getString("status")),
            rs.getInt("running_runs"),
            rs.getInt("max_runs"),
            rs.getInt("weight"),
            instant(rs, "last_heartbeat_at"),
            readCapabilities(rs.getString("capabilities_json")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"));

    /**
     * 注入 JdbcClient 和 ObjectMapper，能力标签以 JSON 文本持久化。
     */
    public JdbcExecutionNodeRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存执行节点健康和容量快照；存在时更新，缺失时插入。
     */
    @Override
    public ExecutionNode save(ExecutionNode executionNode) {
        if (findById(executionNode.executionNodeId()).isPresent()) {
            jdbcClient.sql("""
                            update execution_nodes
                            set base_url = :baseUrl, status = :status, running_runs = :runningRuns,
                                max_runs = :maxRuns, weight = :weight, last_heartbeat_at = :lastHeartbeatAt,
                                capabilities_json = :capabilitiesJson, trace_id = :traceId,
                                created_at = :createdAt, updated_at = :updatedAt
                            where execution_node_id = :executionNodeId
                            """)
                    .param("executionNodeId", executionNode.executionNodeId().value())
                    .param("baseUrl", executionNode.baseUrl())
                    .param("status", executionNode.status().name())
                    .param("runningRuns", executionNode.runningRuns())
                    .param("maxRuns", executionNode.maxRuns())
                    .param("weight", executionNode.weight())
                    .param("lastHeartbeatAt", timestamp(executionNode.lastHeartbeatAt()))
                    .param("capabilitiesJson", writeCapabilities(executionNode.capabilities()))
                    .param("traceId", executionNode.traceId())
                    .param("createdAt", timestamp(executionNode.createdAt()))
                    .param("updatedAt", timestamp(executionNode.updatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into execution_nodes(
                                execution_node_id, base_url, status, running_runs, max_runs, weight,
                                last_heartbeat_at, capabilities_json, trace_id, created_at, updated_at)
                            values (
                                :executionNodeId, :baseUrl, :status, :runningRuns, :maxRuns, :weight,
                                :lastHeartbeatAt, :capabilitiesJson, :traceId, :createdAt, :updatedAt)
                            """)
                    .param("executionNodeId", executionNode.executionNodeId().value())
                    .param("baseUrl", executionNode.baseUrl())
                    .param("status", executionNode.status().name())
                    .param("runningRuns", executionNode.runningRuns())
                    .param("maxRuns", executionNode.maxRuns())
                    .param("weight", executionNode.weight())
                    .param("lastHeartbeatAt", timestamp(executionNode.lastHeartbeatAt()))
                    .param("capabilitiesJson", writeCapabilities(executionNode.capabilities()))
                    .param("traceId", executionNode.traceId())
                    .param("createdAt", timestamp(executionNode.createdAt()))
                    .param("updatedAt", timestamp(executionNode.updatedAt()))
                    .update();
        }
        return executionNode;
    }

    /**
     * 按执行节点 ID 查询节点配置和健康快照。
     */
    @Override
    public Optional<ExecutionNode> findById(ExecutionNodeId executionNodeId) {
        return jdbcClient.sql("""
                        select execution_node_id, base_url, status, running_runs, max_runs, weight,
                               last_heartbeat_at, capabilities_json, trace_id, created_at, updated_at
                        from execution_nodes
                        where execution_node_id = :executionNodeId
                        """)
                .param("executionNodeId", executionNodeId.value())
                .query(rowMapper)
                .optional();
    }

    /**
     * 查询可路由节点，只返回 READY 且仍有容量的节点，并按负载、权重和更新时间排序。
     */
    @Override
    public List<ExecutionNode> findRoutableNodes(int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }
        return jdbcClient.sql("""
                        select execution_node_id, base_url, status, running_runs, max_runs, weight,
                               last_heartbeat_at, capabilities_json, trace_id, created_at, updated_at
                        from execution_nodes
                        where status = :status and running_runs < max_runs
                        order by running_runs asc, weight desc, updated_at asc
                        limit :limit
                        """)
                .param("status", ExecutionNodeStatus.READY.name())
                .param("limit", limit)
                .query(rowMapper)
                .list();
    }

    /**
     * 将能力集合序列化为 JSON 文本，便于后续切换 JSONB 时集中调整。
     */
    private String writeCapabilities(Set<String> capabilities) {
        try {
            return objectMapper.writeValueAsString(capabilities);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "ExecutionNode capabilities 序列化失败", Map.of(), exception);
        }
    }

    /**
     * 从 JSON 文本恢复能力集合。
     */
    private Set<String> readCapabilities(String json) {
        try {
            return objectMapper.readValue(json, CAPABILITIES_TYPE);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "ExecutionNode capabilities 反序列化失败", Map.of(), exception);
        }
    }
}
