package com.enterprise.testagent.workspace;

/**
 * Agent 配置进度发布端口，workspace-management 不直接依赖 WebSocket 实现。
 */
public interface AgentConfigProgressSink {

    AgentConfigProgressSink NOOP = event -> { };

    void publish(AgentConfigProgressEvent event);
}
