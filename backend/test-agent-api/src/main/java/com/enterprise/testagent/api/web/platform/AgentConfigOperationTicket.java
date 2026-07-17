package com.enterprise.testagent.api.web.platform;

import java.time.Instant;

/**
 * Agent 配置进度 WebSocket 一次性 ticket。
 */
record AgentConfigOperationTicket(
        String ticket,
        String operationId,
        String userId,
        boolean superAdmin,
        String traceId,
        Instant expiresAt) {
}
