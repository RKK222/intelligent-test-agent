package com.enterprise.testagent.opencode.runtime.process.socket;

import java.time.Instant;

/**
 * 提供给 WebSocket 后端列表响应和兼容诊断接口的后端实例直连端点。
 */
public record ManagerBackendEndpoint(
        String backendProcessId,
        String linuxServerId,
        String listenUrl,
        String webSocketUrl,
        Instant lastHeartbeatAt) {
}
