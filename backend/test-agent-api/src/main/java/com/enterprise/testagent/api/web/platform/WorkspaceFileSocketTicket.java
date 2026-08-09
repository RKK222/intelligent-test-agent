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
        Instant expiresAt,
        String shareId,
        Long shareVersion,
        String shareActorUserId,
        String executionOwnerUserId,
        boolean shareCanChat,
        Instant shareExpiresAt,
        String shareSessionId) {

    /** 兼容尚未绑定 Session 字段的测试与旧 JVM 内构造路径。 */
    WorkspaceFileSocketTicket(
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
            Instant expiresAt,
            String shareId,
            Long shareVersion,
            String shareActorUserId,
            String executionOwnerUserId,
            boolean shareCanChat,
            Instant shareExpiresAt) {
        this(ticket, workspaceId, linuxServerId, agentLinuxServerId, appSourceWorkspace,
                superAdmin, appAdmin, userId, mode, scope, worktreeId, supportReadOnly,
                supportGrantId, supportGrantTokenDigest, supportActorSessionDigest,
                supportTargetUserId, traceId, expiresAt, shareId, shareVersion,
                shareActorUserId, executionOwnerUserId, shareCanChat, shareExpiresAt, null);
    }

    /** 兼容分享范围字段加入前的完整 ticket 构造器。 */
    WorkspaceFileSocketTicket(
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
        this(ticket, workspaceId, linuxServerId, agentLinuxServerId, appSourceWorkspace,
                superAdmin, appAdmin, userId, mode, scope, worktreeId, supportReadOnly,
                supportGrantId, supportGrantTokenDigest, supportActorSessionDigest,
                supportTargetUserId, traceId, expiresAt, null, null, null, null, false, null);
    }

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
                userId, mode, scope, worktreeId, false, null, null, null, null, traceId, expiresAt,
                null, null, null, null, false, null);
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
                null, mode, scope, worktreeId, false, null, null, null, null, traceId, expiresAt,
                null, null, null, null, false, null);
    }

    boolean sharedSession() {
        return shareId != null;
    }
}
