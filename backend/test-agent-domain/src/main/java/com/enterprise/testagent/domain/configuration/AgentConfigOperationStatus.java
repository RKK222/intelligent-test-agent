package com.enterprise.testagent.domain.configuration;

/**
 * Agent 配置 Git 长操作状态，供 WebSocket 进度和历史查询使用。
 */
public enum AgentConfigOperationStatus {
    RUNNING,
    SUCCEEDED,
    FAILED
}
