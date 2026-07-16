package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.configuration.AgentConfigOperation;
import com.enterprise.testagent.domain.configuration.AgentConfigOperationStatus;
import com.enterprise.testagent.domain.configuration.AgentConfigOperationStep;
import com.enterprise.testagent.domain.configuration.AgentConfigRepository;
import com.enterprise.testagent.domain.configuration.AgentConfigScope;
import com.enterprise.testagent.domain.configuration.AgentConfigWorktree;
import com.enterprise.testagent.domain.configuration.AgentConfigWorktreeStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AgentConfig 的 MyBatis Repository 实现，负责领域端口和 XML mapper 之间的转换。
 */
@Repository
public class MyBatisAgentConfigRepository implements AgentConfigRepository {

    private final AgentConfigMapper mapper;

    /**
     * 注入 MyBatis mapper；连接、事务和 SQL 执行由 MyBatis-Spring 管理。
     */
    public MyBatisAgentConfigRepository(AgentConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AgentConfigOperation saveOperation(AgentConfigOperation operation) {
        AgentConfigOperationRow row = toRow(operation);
        if (mapper.findOperation(operation.operationId()) == null) {
            mapper.insertOperation(row);
        } else {
            mapper.updateOperation(row);
        }
        return findOperation(operation.operationId()).orElseThrow();
    }

    @Override
    public Optional<AgentConfigOperation> findOperation(String operationId) {
        return Optional.ofNullable(mapper.findOperation(operationId)).map(this::toDomain);
    }

    @Override
    public AgentConfigWorktree saveWorktree(AgentConfigWorktree worktree) {
        AgentConfigWorktreeRow row = toRow(worktree);
        if (mapper.findWorktree(worktree.worktreeId()) == null) {
            mapper.insertWorktree(row);
        } else {
            mapper.updateWorktree(row);
        }
        return findWorktree(worktree.worktreeId()).orElseThrow();
    }

    @Override
    public Optional<AgentConfigWorktree> findWorktree(String worktreeId) {
        return Optional.ofNullable(mapper.findWorktree(worktreeId)).map(this::toDomain);
    }

    @Override
    public List<AgentConfigWorktree> findWorktrees(AgentConfigScope scope, WorkspaceId workspaceId, UserId createdBy) {
        return findWorktrees(scope, workspaceId, createdBy, null, null);
    }

    @Override
    public List<AgentConfigWorktree> findWorktrees(
            AgentConfigScope scope,
            WorkspaceId workspaceId,
            UserId createdBy,
            String linuxServerId,
            AgentConfigWorktreeStatus status) {
        return mapper.findWorktrees(
                        scope.name(),
                        workspaceId == null ? null : workspaceId.value(),
                        createdBy == null ? null : createdBy.value(),
                        linuxServerId == null || linuxServerId.isBlank() ? null : linuxServerId.trim(),
                        status == null ? null : status.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    private AgentConfigOperationRow toRow(AgentConfigOperation operation) {
        return new AgentConfigOperationRow(
                operation.operationId(),
                operation.scope().name(),
                operation.workspaceId() == null ? null : operation.workspaceId().value(),
                operation.action(),
                operation.status().name(),
                operation.currentStep().name(),
                operation.errorCode(),
                operation.errorMessage(),
                operation.traceId(),
                operation.branch(),
                operation.commitHash(),
                operation.createdAt(),
                operation.updatedAt());
    }

    private AgentConfigOperation toDomain(AgentConfigOperationRow row) {
        return new AgentConfigOperation(
                row.operationId(),
                AgentConfigScope.valueOf(row.scope()),
                optionalWorkspaceId(row.workspaceId()),
                row.action(),
                AgentConfigOperationStatus.valueOf(row.status()),
                AgentConfigOperationStep.valueOf(row.currentStep()),
                row.errorCode(),
                row.errorMessage(),
                row.traceId(),
                row.branch(),
                row.commitHash(),
                row.createdAt(),
                row.updatedAt());
    }

    private AgentConfigWorktreeRow toRow(AgentConfigWorktree worktree) {
        return new AgentConfigWorktreeRow(
                worktree.worktreeId(),
                worktree.scope().name(),
                worktree.workspaceId() == null ? null : worktree.workspaceId().value(),
                worktree.linuxServerId(),
                worktree.worktreeName(),
                worktree.branch(),
                worktree.rootPath(),
                worktree.createdBy().value(),
                worktree.status().name(),
                worktree.createdAt(),
                worktree.updatedAt());
    }

    private AgentConfigWorktree toDomain(AgentConfigWorktreeRow row) {
        return new AgentConfigWorktree(
                row.worktreeId(),
                AgentConfigScope.valueOf(row.scope()),
                optionalWorkspaceId(row.workspaceId()),
                row.linuxServerId(),
                row.worktreeName(),
                row.branch(),
                row.rootPath(),
                new UserId(row.createdByUserId()),
                AgentConfigWorktreeStatus.valueOf(row.status()),
                row.createdAt(),
                row.updatedAt());
    }

    private static WorkspaceId optionalWorkspaceId(String value) {
        return value == null || value.isBlank() ? null : new WorkspaceId(value);
    }
}
