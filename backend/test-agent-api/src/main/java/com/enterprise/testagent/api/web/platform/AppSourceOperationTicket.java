package com.enterprise.testagent.api.web.platform;

import java.time.Instant;

/** 应用源码进度 WebSocket 的用户、operation 与签发 JVM 绑定 ticket。 */
record AppSourceOperationTicket(
        String ticket,
        String operationId,
        String userId,
        boolean appAdmin,
        String issuerBackendProcessId,
        String traceId,
        Instant expiresAt) {
}
