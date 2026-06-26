package com.icbc.testagent.domain.configuration;

import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
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
}
