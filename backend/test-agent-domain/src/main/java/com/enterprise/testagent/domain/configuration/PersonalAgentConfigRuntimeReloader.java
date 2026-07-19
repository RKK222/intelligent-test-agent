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
}
