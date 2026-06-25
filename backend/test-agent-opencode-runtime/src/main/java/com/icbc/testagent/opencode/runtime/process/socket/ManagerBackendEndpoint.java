package com.icbc.testagent.opencode.runtime.process.socket;

import java.time.Instant;

/**
 * 提供给 opencode-manager discovery API 的后端实例直连端点。
 */
public record ManagerBackendEndpoint(
        String backendProcessId,
        String linuxServerId,
        String listenUrl,
        String webSocketUrl,
        Instant lastHeartbeatAt) {
}
