package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.domain.runtime.RuntimeKind;
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
        String shareSessionId,
        RuntimeKind runtimeKind,
        String localClientInstanceId,
        Long connectionGeneration,
        String rootDigest,
        String unifiedAuthId) {

    WorkspaceFileSocketTicket {
        runtimeKind = RuntimeKind.fromNullable(runtimeKind);
        if (runtimeKind == RuntimeKind.LOCAL_CLIENT
                && (localClientInstanceId == null || localClientInstanceId.isBlank()
                || connectionGeneration == null || connectionGeneration < 1)) {
            throw new IllegalArgumentException("local client ticket requires instance and generation");
        }
    }

    /** 兼容本地运行目标和统一认证标识加入前的完整 ticket 构造器。 */
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
            Instant shareExpiresAt,
            String shareSessionId) {
        this(ticket, workspaceId, linuxServerId, agentLinuxServerId, appSourceWorkspace,
                superAdmin, appAdmin, userId, mode, scope, worktreeId, supportReadOnly,
                supportGrantId, supportGrantTokenDigest, supportActorSessionDigest,
                supportTargetUserId, traceId, expiresAt, shareId, shareVersion,
                shareActorUserId, executionOwnerUserId, shareCanChat, shareExpiresAt,
                shareSessionId, RuntimeKind.SERVER_PROCESS, null, null, null, null);
    }

    /** 兼容仅绑定统一认证标识的服务端 ticket 构造路径。 */
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
            Instant shareExpiresAt,
            String shareSessionId,
            String unifiedAuthId) {
        this(ticket, workspaceId, linuxServerId, agentLinuxServerId, appSourceWorkspace,
                superAdmin, appAdmin, userId, mode, scope, worktreeId, supportReadOnly,
                supportGrantId, supportGrantTokenDigest, supportActorSessionDigest,
                supportTargetUserId, traceId, expiresAt, shareId, shareVersion,
                shareActorUserId, executionOwnerUserId, shareCanChat, shareExpiresAt,
                shareSessionId, RuntimeKind.SERVER_PROCESS, null, null, null, unifiedAuthId);
    }

    /** 兼容仅绑定本地运行目标、尚未绑定统一认证标识的构造路径。 */
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
            Instant shareExpiresAt,
            String shareSessionId,
            RuntimeKind runtimeKind,
            String localClientInstanceId,
            Long connectionGeneration,
            String rootDigest) {
        this(ticket, workspaceId, linuxServerId, agentLinuxServerId, appSourceWorkspace,
                superAdmin, appAdmin, userId, mode, scope, worktreeId, supportReadOnly,
                supportGrantId, supportGrantTokenDigest, supportActorSessionDigest,
                supportTargetUserId, traceId, expiresAt, shareId, shareVersion,
                shareActorUserId, executionOwnerUserId, shareCanChat, shareExpiresAt,
                shareSessionId, runtimeKind, localClientInstanceId, connectionGeneration, rootDigest, null);
    }

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
                shareActorUserId, executionOwnerUserId, shareCanChat, shareExpiresAt, null, null);
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
                null, null, null, null, false, null, null);
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
                null, null, null, null, false, null, null);
    }

    boolean sharedSession() {
        return shareId != null;
    }

    boolean localClient() {
        return runtimeKind == RuntimeKind.LOCAL_CLIENT;
    }

    /** 团队只读票据复用既有只读字段存储范围快照，但与支持排查授权严格分流。 */
    boolean teamReadOnly() {
        return supportReadOnly && supportGrantId != null && supportGrantId.startsWith("TEAM:");
    }
}
