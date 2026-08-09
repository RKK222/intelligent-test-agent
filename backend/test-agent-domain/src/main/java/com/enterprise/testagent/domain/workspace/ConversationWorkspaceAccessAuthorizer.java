package com.enterprise.testagent.domain.workspace;

import com.enterprise.testagent.domain.user.UserId;

/**
 * 为会话运行上下文校验 Workspace 的当前业务权限；runtime 只依赖该端口，不直接读取应用成员表。
 */
public interface ConversationWorkspaceAccessAuthorizer {

    /** 文件访问授权时识别的业务工作区类型；分类与授权必须来自同一次权威判断。 */
    enum FileWorkspaceKind {
        STANDARD,
        APP_SOURCE,
        EXPERIENCE
    }

    /**
     * 托管 Workspace 必须属于已启用应用且当前用户仍是有效成员；非托管历史 Workspace 沿用 Session owner 规则。
     */
    void requireAccess(UserId userId, WorkspaceId workspaceId);

    /**
     * 文件通道除托管 Workspace 成员校验外默认拒绝非托管 Workspace；仅服务器工作区兼容入口可显式放行超级管理员。
     *
     * <p>默认实现保留端口的函数式接口兼容性，具体托管实现负责区分非托管 Workspace。
     */
    default void requireFileAccess(UserId userId, WorkspaceId workspaceId, boolean allowUnmanagedWorkspace) {
        requireAccess(userId, workspaceId);
    }

    /**
     * 执行文件授权并返回同一次判断识别出的工作区类型，供短期 ticket 固定安全边界。
     *
     * <p>兼容实现默认视为普通工作区；托管实现必须在同一次 Repository 读取中返回精确分类。
     */
    default FileWorkspaceKind requireClassifiedFileAccess(
            UserId userId,
            WorkspaceId workspaceId,
            boolean allowUnmanagedWorkspace) {
        requireFileAccess(userId, workspaceId, allowUnmanagedWorkspace);
        return FileWorkspaceKind.STANDARD;
    }
}
