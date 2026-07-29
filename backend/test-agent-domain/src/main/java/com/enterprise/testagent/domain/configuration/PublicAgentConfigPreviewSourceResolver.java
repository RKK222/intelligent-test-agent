package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.user.UserId;
import java.util.Optional;

/**
 * 解析用户进程启动时可直接加载的公共个人配置目录。
 *
 * <p>实现方负责校验 worktree 所有权、服务器归属和受管目录边界；运行时只消费可信的绝对配置路径，
 * 不反向读取工作区仓储或执行 Git 操作。</p>
 */
public interface PublicAgentConfigPreviewSourceResolver {

    /**
     * 返回当前用户在目标服务器上的有效公共个人配置目录；没有可用 worktree 时返回空并使用共享配置。
     */
    Optional<String> resolvePublicPersonalConfigPath(UserId userId, String linuxServerId);
}
