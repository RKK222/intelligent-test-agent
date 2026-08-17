package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.runtime.RuntimeKind;

/**
 * 工作空间文件 WebSocket 路由结果，前端据此向目标后端申请短期 ticket。
 */
public record WorkspaceFileRouteResponse(
        String workspaceId,
        String linuxServerId,
        String baseUrl,
        String webSocketPath,
        boolean sameServer,
        String message,
        RuntimeKind runtimeKind,
        String localClientInstanceId,
        Long connectionGeneration,
        String rootDigest,
        boolean online) {

    /** 保持既有服务器工作区构造路径和 JSON 语义兼容。 */
    public WorkspaceFileRouteResponse(
            String workspaceId,
            String linuxServerId,
            String baseUrl,
            String webSocketPath,
            boolean sameServer,
            String message) {
        this(workspaceId, linuxServerId, baseUrl, webSocketPath, sameServer, message,
                RuntimeKind.SERVER_PROCESS, null, null, null, true);
    }
}
