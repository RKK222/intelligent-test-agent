package com.icbc.testagent.domain.configuration;

/**
 * Agent 配置作用域：公共级由全局 Git 仓库管理，工作空间级绑定具体 Workspace。
 */
public enum AgentConfigScope {
    PUBLIC,
    WORKSPACE
}
