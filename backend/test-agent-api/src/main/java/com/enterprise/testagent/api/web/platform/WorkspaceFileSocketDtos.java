package com.enterprise.testagent.api.web.platform;

import java.time.Instant;

/**
 * 工作空间文件 WebSocket 相关 HTTP DTO。
 */
final class WorkspaceFileSocketDtos {

    private WorkspaceFileSocketDtos() {
    }

    record TicketRequest(
            String workspaceId,
            String linuxServerId,
            String mode,
            String scope,
            String worktreeId,
            String localClientInstanceId,
            Long connectionGeneration) {
        TicketRequest(String workspaceId, String linuxServerId, String mode) {
            this(workspaceId, linuxServerId, mode, null, null, null, null);
        }

        TicketRequest(String workspaceId, String linuxServerId, String mode, String scope, String worktreeId) {
            this(workspaceId, linuxServerId, mode, scope, worktreeId, null, null);
        }
    }

    record TicketResponse(String ticket, Instant expiresAt, String webSocketUrl) {
    }
}
