package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;

/**
 * Agent 配置操作和 worktree 记录端口，domain 只定义契约，具体持久化由 persistence 实现。
 */
public interface AgentConfigRepository {

    AgentConfigOperation saveOperation(AgentConfigOperation operation);

    Optional<AgentConfigOperation> findOperation(String operationId);

    AgentConfigWorktree saveWorktree(AgentConfigWorktree worktree);

    Optional<AgentConfigWorktree> findWorktree(String worktreeId);

    List<AgentConfigWorktree> findWorktrees(AgentConfigScope scope, WorkspaceId workspaceId, UserId createdBy);

    /**
     * 按可选服务器和状态过滤 worktree；存量 JDBC 实现复用旧查询后在内存中过滤，MyBatis 实现用 XML 下推条件。
     */
    default List<AgentConfigWorktree> findWorktrees(
            AgentConfigScope scope,
            WorkspaceId workspaceId,
            UserId createdBy,
            String linuxServerId,
            AgentConfigWorktreeStatus status) {
        String normalizedServer = linuxServerId == null || linuxServerId.isBlank() ? null : linuxServerId.trim();
        return findWorktrees(scope, workspaceId, createdBy).stream()
                .filter(worktree -> normalizedServer == null || normalizedServer.equals(worktree.linuxServerId()))
                .filter(worktree -> status == null || status == worktree.status())
                .toList();
    }
}
