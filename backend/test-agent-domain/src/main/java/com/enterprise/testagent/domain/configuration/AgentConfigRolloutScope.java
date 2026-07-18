package com.enterprise.testagent.domain.configuration;

/** 共享 Agent 配置发布排空范围。个人 worktree 热加载不进入该持久化全局链路。 */
public enum AgentConfigRolloutScope {
    PUBLIC,
    APPLICATION
}
