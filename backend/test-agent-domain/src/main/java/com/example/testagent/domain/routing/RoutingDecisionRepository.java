package com.example.testagent.domain.routing;

import com.example.testagent.domain.run.RunId;
import java.util.Optional;

/**
 * 路由决策持久化端口，用于记录 Run 被派发到哪个执行节点及其原因。
 */
public interface RoutingDecisionRepository {

    RoutingDecision save(RoutingDecision routingDecision);

    Optional<RoutingDecision> findByRunId(RunId runId);
}
