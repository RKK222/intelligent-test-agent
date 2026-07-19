package com.enterprise.testagent.domain.configuration;

/**
 * 个人 Agent 配置运行态重载结果。
 *
 * @param reloaded 是否已经刷新并 dispose 当前用户的运行实例
 * @param message 面向前端的安全结果说明
 */
public record PersonalAgentConfigRuntimeReloadResult(boolean reloaded, String message) {
}
