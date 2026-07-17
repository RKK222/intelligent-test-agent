package com.enterprise.testagent.domain.routing;

/**
 * 路由原因用于后续观测和问题排查，避免只记录选中节点而丢失决策依据。
 */
public enum RoutingReason {
    STICKY_SESSION,
    LOWEST_LOAD,
    RECOVERED_NODE,
    MANUAL_OVERRIDE
}
