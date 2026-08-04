package com.enterprise.testagent.domain.managedworkspace;

/**
 * 个人工作区跨服务器搬迁状态。
 *
 * <p>目标端只有在快照完整恢复并校验后才进入 {@link #CLEANUP_PENDING}；该状态表示数据库已经
 * 切到目标服务器，但源端旧 worktree 仍需由源服务器继续清理。</p>
 */
public enum PersonalWorkspaceRelocationStatus {
    DISCOVERED,
    EXPORTING,
    TRANSFERRING,
    APPLYING,
    CLEANUP_PENDING,
    RETRY_WAIT,
    SUCCEEDED,
    CANCELLED
}
