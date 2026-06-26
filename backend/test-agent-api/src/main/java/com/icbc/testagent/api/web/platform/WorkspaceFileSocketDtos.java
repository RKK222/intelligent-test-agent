package com.icbc.testagent.api.web.platform;

import java.time.Instant;

/**
 * 工作空间文件 WebSocket 相关 HTTP DTO。
 */
final class WorkspaceFileSocketDtos {

    private WorkspaceFileSocketDtos() {
    }

    record TicketRequest(String workspaceId, String linuxServerId, String mode) {
    }

    record TicketResponse(String ticket, Instant expiresAt, String webSocketUrl) {
    }
}
