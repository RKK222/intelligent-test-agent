package com.icbc.testagent.opencode.runtime.process;

/**
 * 工作空间文件 WebSocket 路由结果，前端据此向目标后端申请短期 ticket。
 */
public record WorkspaceFileRouteResponse(
        String workspaceId,
        String linuxServerId,
        String baseUrl,
        String webSocketPath,
        boolean sameServer,
        String message) {
}
