package com.example.testagent.domain.node;

import java.util.List;
import java.util.Optional;

/**
 * 执行节点持久化端口，路由服务通过该端口读取健康且有容量的候选节点。
 */
public interface ExecutionNodeRepository {

    ExecutionNode save(ExecutionNode executionNode);

    Optional<ExecutionNode> findById(ExecutionNodeId executionNodeId);

    List<ExecutionNode> findRoutableNodes(int limit);
}
