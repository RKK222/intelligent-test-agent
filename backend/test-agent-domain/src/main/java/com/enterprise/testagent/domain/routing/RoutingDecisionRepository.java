package com.enterprise.testagent.domain.routing;

import com.enterprise.testagent.domain.run.RunId;
import java.util.Optional;

/**
 * 路由决策持久化端口，用于记录 Run 被派发到哪个执行节点及其原因。
 */
public interface RoutingDecisionRepository {

    /**
     * 保存运行到执行节点的路由决策。
     */
    RoutingDecision save(RoutingDecision routingDecision);

    /**
     * 按运行 ID 查询已记录的路由决策。
     */
    Optional<RoutingDecision> findByRunId(RunId runId);
}
