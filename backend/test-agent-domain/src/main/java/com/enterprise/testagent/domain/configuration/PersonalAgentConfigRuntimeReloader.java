package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.user.UserId;

/**
 * 个人 Agent 配置运行态重载端口。
 *
 * <p>公共个人 worktree 的磁盘路径由 workspace-management 校验，具体用户进程定位、
 * 有效配置刷新和 OpenCode dispose 由 opencode-runtime 实现，避免工作区模块反向依赖运行时实现。
 */
public interface PersonalAgentConfigRuntimeReloader {

    PersonalAgentConfigRuntimeReloadResult reloadPublicPreview(
            UserId userId,
            String linuxServerId,
            String sourceConfigPath,
            String traceId);

    /** Tool 脚本变化时显式要求重建进程；默认实现保持旧调用方的 dispose 语义。 */
    default PersonalAgentConfigRuntimeReloadResult reloadPublicPreview(
            UserId userId,
            String linuxServerId,
            String sourceConfigPath,
            String traceId,
            boolean processRestartRequired) {
        return reloadPublicPreview(userId, linuxServerId, sourceConfigPath, traceId);
    }

    /**
     * 初始化完成后激活公共个人配置；若进程启动前已经直接加载同一路径，实现方可跳过重复 dispose。
     */
    default PersonalAgentConfigRuntimeReloadResult activatePublicPreview(
            UserId userId,
            String linuxServerId,
            String sourceConfigPath,
            String traceId) {
        return reloadPublicPreview(userId, linuxServerId, sourceConfigPath, traceId);
    }

    /**
     * 工作树内 {@code .opencode/opencode.jsonc} 已更新时，只释放当前用户 OpenCode 配置缓存，
     * 不切换公共 Agent 配置软链接。
     */
    default PersonalAgentConfigRuntimeReloadResult reloadWorkspaceConfiguration(
            UserId userId,
            String linuxServerId,
            String traceId) {
        return new PersonalAgentConfigRuntimeReloadResult(false, "当前运行时不支持工作树配置热加载");
    }
}
