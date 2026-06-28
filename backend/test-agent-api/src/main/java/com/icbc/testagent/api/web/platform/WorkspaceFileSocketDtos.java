package com.icbc.testagent.api.web.platform;

import java.time.Instant;

/**
 * 工作空间文件 WebSocket 相关 HTTP DTO。
 */
final class WorkspaceFileSocketDtos {

    private WorkspaceFileSocketDtos() {
    }

    record TicketRequest(String workspaceId, String linuxServerId, String mode, String scope, String worktreeId) {
        TicketRequest(String workspaceId, String linuxServerId, String mode) {
            this(workspaceId, linuxServerId, mode, null, null);
        }
    }

    record TicketResponse(String ticket, Instant expiresAt, String webSocketUrl) {
    }
}
