package com.icbc.testagent.api.web.platform;

import java.time.Instant;

/**
 * 工作空间文件 WebSocket 一次性 ticket，upgrade 后只保留已校验过的最小上下文。
 */
record WorkspaceFileSocketTicket(
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
}
