package com.enterprise.testagent.api.web.platform;

import java.time.Instant;

/**
 * 工作空间文件 WebSocket 一次性 ticket，upgrade 后只保留已校验过的最小上下文。
 */
record WorkspaceFileSocketTicket(
        String ticket,
        String workspaceId,
        String linuxServerId,
        String agentLinuxServerId,
        boolean appSourceWorkspace,
        boolean superAdmin,
        boolean appAdmin,
        String userId,
        String mode,
        String scope,
        String worktreeId,
        boolean supportReadOnly,
        String supportGrantId,
        String supportGrantTokenDigest,
        String supportActorSessionDigest,
        String supportTargetUserId,
        String traceId,
        Instant expiresAt) {

    WorkspaceFileSocketTicket(
            String ticket,
            String workspaceId,
            String linuxServerId,
            String agentLinuxServerId,
            boolean superAdmin,
            boolean appAdmin,
            String userId,
            String mode,
            String scope,
            String worktreeId,
            String traceId,
            Instant expiresAt) {
        this(ticket, workspaceId, linuxServerId, agentLinuxServerId, false, superAdmin, appAdmin,
                userId, mode, scope, worktreeId, false, null, null, null, null, traceId, expiresAt);
    }

    WorkspaceFileSocketTicket(
            String ticket,
            String workspaceId,
            String linuxServerId,
            String agentLinuxServerId,
            boolean superAdmin,
            String mode,
            String scope,
            String worktreeId,
            String traceId,
            Instant expiresAt) {
        this(ticket, workspaceId, linuxServerId, agentLinuxServerId, false, superAdmin, superAdmin,
                null, mode, scope, worktreeId, false, null, null, null, null, traceId, expiresAt);
    }
}
