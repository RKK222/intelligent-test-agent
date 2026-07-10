package com.icbc.testagent.domain.workspace;

import com.icbc.testagent.domain.user.UserId;

/**
 * 为会话运行上下文校验 Workspace 的当前业务权限；runtime 只依赖该端口，不直接读取应用成员表。
 */
public interface ConversationWorkspaceAccessAuthorizer {

    /**
     * 托管 Workspace 必须属于已启用应用且当前用户仍是有效成员；非托管历史 Workspace 沿用 Session owner 规则。
     */
    void requireAccess(UserId userId, WorkspaceId workspaceId);
}
