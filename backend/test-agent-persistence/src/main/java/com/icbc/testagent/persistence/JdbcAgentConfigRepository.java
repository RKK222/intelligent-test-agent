package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.configuration.AgentConfigOperation;
import com.icbc.testagent.domain.configuration.AgentConfigOperationStatus;
import com.icbc.testagent.domain.configuration.AgentConfigOperationStep;
import com.icbc.testagent.domain.configuration.AgentConfigRepository;
import com.icbc.testagent.domain.configuration.AgentConfigScope;
import com.icbc.testagent.domain.configuration.AgentConfigWorktree;
import com.icbc.testagent.domain.configuration.AgentConfigWorktreeStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Agent 配置操作 JDBC Repository，保存 operation 快照和 worktree 生命周期。
 */
public class JdbcAgentConfigRepository extends JdbcRepositorySupport implements AgentConfigRepository {

    private final JdbcClient jdbcClient;

    private final RowMapper<AgentConfigOperation> operationMapper = (rs, rowNum) -> new AgentConfigOperation(
            rs.getString("operation_id"),
            AgentConfigScope.valueOf(rs.getString("scope")),
            optionalWorkspaceId(rs.getString("workspace_id")),
            rs.getString("action"),
            AgentConfigOperationStatus.valueOf(rs.getString("status")),
            AgentConfigOperationStep.valueOf(rs.getString("current_step")),
            rs.getString("error_code"),
            rs.getString("error_message"),
            rs.getString("trace_id"),
            rs.getString("branch"),
            rs.getString("commit_hash"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private final RowMapper<AgentConfigWorktree> worktreeMapper = (rs, rowNum) -> new AgentConfigWorktree(
            rs.getString("worktree_id"),
            AgentConfigScope.valueOf(rs.getString("scope")),
            optionalWorkspaceId(rs.getString("workspace_id")),
            null,
            rs.getString("worktree_name"),
            rs.getString("branch"),
            rs.getString("root_path"),
            new UserId(rs.getString("created_by_user_id")),
            AgentConfigWorktreeStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    public JdbcAgentConfigRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public AgentConfigOperation saveOperation(AgentConfigOperation operation) {
        if (findOperation(operation.operationId()).isPresent()) {
            jdbcClient.sql("""
                            update agent_config_operations
                            set scope = :scope,
                                workspace_id = :workspaceId,
                                action = :action,
                                status = :status,
                                current_step = :currentStep,
                                error_code = :errorCode,
                                error_message = :errorMessage,
                                trace_id = :traceId,
                                branch = :branch,
                                commit_hash = :commitHash,
                                updated_at = :updatedAt
                            where operation_id = :operationId
                            """)
                    .param("operationId", operation.operationId())
                    .param("scope", operation.scope().name())
                    .param("workspaceId", operation.workspaceId() == null ? null : operation.workspaceId().value())
                    .param("action", operation.action())
                    .param("status", operation.status().name())
                    .param("currentStep", operation.currentStep().name())
                    .param("errorCode", operation.errorCode())
                    .param("errorMessage", operation.errorMessage())
                    .param("traceId", operation.traceId())
                    .param("branch", operation.branch())
                    .param("commitHash", operation.commitHash())
                    .param("updatedAt", timestamp(operation.updatedAt()))
                    .update();
            return findOperation(operation.operationId()).orElseThrow();
        }
        jdbcClient.sql("""
                        insert into agent_config_operations(
                            operation_id, scope, workspace_id, action, status, current_step,
                            error_code, error_message, trace_id, branch, commit_hash, created_at, updated_at)
                        values (
                            :operationId, :scope, :workspaceId, :action, :status, :currentStep,
                            :errorCode, :errorMessage, :traceId, :branch, :commitHash, :createdAt, :updatedAt)
                        """)
                .param("operationId", operation.operationId())
                .param("scope", operation.scope().name())
                .param("workspaceId", operation.workspaceId() == null ? null : operation.workspaceId().value())
                .param("action", operation.action())
                .param("status", operation.status().name())
                .param("currentStep", operation.currentStep().name())
                .param("errorCode", operation.errorCode())
                .param("errorMessage", operation.errorMessage())
                .param("traceId", operation.traceId())
                .param("branch", operation.branch())
                .param("commitHash", operation.commitHash())
                .param("createdAt", timestamp(operation.createdAt()))
                .param("updatedAt", timestamp(operation.updatedAt()))
                .update();
        return findOperation(operation.operationId()).orElseThrow();
    }

    @Override
    public Optional<AgentConfigOperation> findOperation(String operationId) {
        return jdbcClient.sql("""
                        select operation_id, scope, workspace_id, action, status, current_step,
                               error_code, error_message, trace_id, branch, commit_hash, created_at, updated_at
                        from agent_config_operations
                        where operation_id = :operationId
                        """)
                .param("operationId", operationId)
                .query(operationMapper)
                .optional();
    }

    @Override
    public AgentConfigWorktree saveWorktree(AgentConfigWorktree worktree) {
        if (findWorktree(worktree.worktreeId()).isPresent()) {
            jdbcClient.sql("""
                            update agent_config_worktrees
                            set scope = :scope,
                                workspace_id = :workspaceId,
                                worktree_name = :worktreeName,
                                branch = :branch,
                                root_path = :rootPath,
                                created_by_user_id = :createdBy,
                                status = :status,
                                updated_at = :updatedAt
                            where worktree_id = :worktreeId
                            """)
                    .param("worktreeId", worktree.worktreeId())
                    .param("scope", worktree.scope().name())
                    .param("workspaceId", worktree.workspaceId() == null ? null : worktree.workspaceId().value())
                    .param("worktreeName", worktree.worktreeName())
                    .param("branch", worktree.branch())
                    .param("rootPath", worktree.rootPath())
                    .param("createdBy", worktree.createdBy().value())
                    .param("status", worktree.status().name())
                    .param("updatedAt", timestamp(worktree.updatedAt()))
                    .update();
            return findWorktree(worktree.worktreeId()).orElseThrow();
        }
        jdbcClient.sql("""
                        insert into agent_config_worktrees(
                            worktree_id, scope, workspace_id, worktree_name, branch, root_path,
                            created_by_user_id, status, created_at, updated_at)
                        values (
                            :worktreeId, :scope, :workspaceId, :worktreeName, :branch, :rootPath,
                            :createdBy, :status, :createdAt, :updatedAt)
                        """)
                .param("worktreeId", worktree.worktreeId())
                .param("scope", worktree.scope().name())
                .param("workspaceId", worktree.workspaceId() == null ? null : worktree.workspaceId().value())
                .param("worktreeName", worktree.worktreeName())
                .param("branch", worktree.branch())
                .param("rootPath", worktree.rootPath())
                .param("createdBy", worktree.createdBy().value())
                .param("status", worktree.status().name())
                .param("createdAt", timestamp(worktree.createdAt()))
                .param("updatedAt", timestamp(worktree.updatedAt()))
                .update();
        return findWorktree(worktree.worktreeId()).orElseThrow();
    }

    @Override
    public Optional<AgentConfigWorktree> findWorktree(String worktreeId) {
        return jdbcClient.sql("""
                        select worktree_id, scope, workspace_id, worktree_name, branch, root_path,
                               created_by_user_id, status, created_at, updated_at
                        from agent_config_worktrees
                        where worktree_id = :worktreeId
                        """)
                .param("worktreeId", worktreeId)
                .query(worktreeMapper)
                .optional();
    }

    @Override
    public List<AgentConfigWorktree> findWorktrees(AgentConfigScope scope, WorkspaceId workspaceId, UserId createdBy) {
        StringBuilder sql = new StringBuilder("""
                select worktree_id, scope, workspace_id, worktree_name, branch, root_path,
                       created_by_user_id, status, created_at, updated_at
                from agent_config_worktrees
                where scope = :scope
                """);
        if (workspaceId != null) {
            sql.append(" and workspace_id = :workspaceId");
        }
        if (createdBy != null) {
            sql.append(" and created_by_user_id = :createdBy");
        }
        sql.append(" order by updated_at desc, worktree_id");
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString())
                .param("scope", scope.name());
        if (workspaceId != null) {
            spec = spec.param("workspaceId", workspaceId.value());
        }
        if (createdBy != null) {
            spec = spec.param("createdBy", createdBy.value());
        }
        return spec.query(worktreeMapper).list();
    }

    private static WorkspaceId optionalWorkspaceId(String value) {
        return value == null || value.isBlank() ? null : new WorkspaceId(value);
    }
}
