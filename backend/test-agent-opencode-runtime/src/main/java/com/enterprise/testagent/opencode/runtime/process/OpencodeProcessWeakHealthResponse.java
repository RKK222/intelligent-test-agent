package com.enterprise.testagent.opencode.runtime.process;

import java.time.Instant;

/**
 * 前端弱健康检查响应；普通不可用以 healthy=false 表示，不抛平台异常。
 */
public record OpencodeProcessWeakHealthResponse(
        boolean healthy,
        OpencodeProcessWeakHealthStatus status,
        String serviceStatus,
        String linuxServerId,
        String containerId,
        int port,
        String baseUrl,
        Instant checkedAt,
        String message) {
}
