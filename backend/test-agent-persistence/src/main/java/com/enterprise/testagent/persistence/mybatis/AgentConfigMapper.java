package com.enterprise.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Agent 配置 MyBatis mapper；SQL 必须维护在 XML 中，接口只声明参数和返回值。
 */
@Mapper
public interface AgentConfigMapper {

    void insertOperation(AgentConfigOperationRow row);

    int updateOperation(AgentConfigOperationRow row);

    AgentConfigOperationRow findOperation(@Param("operationId") String operationId);

    void insertWorktree(AgentConfigWorktreeRow row);

    int updateWorktree(AgentConfigWorktreeRow row);

    AgentConfigWorktreeRow findWorktree(@Param("worktreeId") String worktreeId);

    List<AgentConfigWorktreeRow> findWorktrees(
            @Param("scope") String scope,
            @Param("workspaceId") String workspaceId,
            @Param("createdByUserId") String createdByUserId,
            @Param("linuxServerId") String linuxServerId,
            @Param("status") String status);
}
