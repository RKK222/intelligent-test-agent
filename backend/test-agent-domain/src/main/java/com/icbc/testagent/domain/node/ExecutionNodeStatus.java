package com.icbc.testagent.domain.node;

/**
 * 执行节点健康与容量状态，具体探活和负载采集由后续持久化/路由阶段实现。
 */
public enum ExecutionNodeStatus {
    READY,
    BUSY,
    UNHEALTHY,
    OFFLINE
}
