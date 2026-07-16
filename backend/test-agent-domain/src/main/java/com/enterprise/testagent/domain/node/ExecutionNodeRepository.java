package com.enterprise.testagent.domain.node;

import java.util.List;
import java.util.Optional;

/**
 * 执行节点持久化端口，路由服务通过该端口读取健康且有容量的候选节点。
 */
public interface ExecutionNodeRepository {

    /**
     * 保存执行节点健康、容量和能力快照。
     */
    ExecutionNode save(ExecutionNode executionNode);

    /**
     * 按执行节点 ID 查询节点。
     */
    Optional<ExecutionNode> findById(ExecutionNodeId executionNodeId);

    /**
     * 查询可路由节点候选，具体过滤和排序由持久化实现保证。
     */
    List<ExecutionNode> findRoutableNodes(int limit);
}
