package com.enterprise.testagent.domain.workspace;

import com.enterprise.testagent.domain.user.UserId;

/**
 * 体验工作区实时访问策略端口，供会话、文件、Git 和运行上下文入口复用同一权威判断。
 */
public interface ExperienceWorkspaceAccessAuthorizer {

    /** 统一识别体验 Workspace 的稳定 ID 命名空间，历史绑定也必须保持体验属性。 */
    static boolean isExperienceWorkspaceId(WorkspaceId workspaceId) {
        return workspaceId != null && workspaceId.value().startsWith("wrk_exp_");
    }

    /** 稳定 ID 命名空间用于识别历史体验 Workspace，确保旧绑定不会降级为普通目录。 */
    default boolean isExperienceWorkspace(WorkspaceId workspaceId) {
        return isExperienceWorkspaceId(workspaceId);
    }

    /** 校验用户资格、当前服务器绑定、配置与 Workspace 状态，并返回当前可访问 Workspace。 */
    Workspace requireAccess(UserId userId, WorkspaceId workspaceId);
}
