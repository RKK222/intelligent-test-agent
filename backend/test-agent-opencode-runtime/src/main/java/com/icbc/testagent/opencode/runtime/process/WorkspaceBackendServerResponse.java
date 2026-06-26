package com.icbc.testagent.opencode.runtime.process;

/**
 * 可承载工作空间文件 WebSocket 的后端服务器摘要。
 */
public record WorkspaceBackendServerResponse(
        String linuxServerId,
        String name,
        String baseUrl,
        String webSocketPath,
        String defaultDirectory,
        boolean sameAsAgent) {
}
