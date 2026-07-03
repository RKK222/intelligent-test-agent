package com.icbc.testagent.workspace;

import com.icbc.testagent.domain.configuration.AgentConfigOperationStatus;
import java.time.Instant;

/**
 * Agent 配置 Git 长操作进度事件，API 层通过 WebSocket 转发给当前浏览器。
 */
public record AgentConfigProgressEvent(
        String operationId,
        String type,
        AgentConfigOperationStatus status,
        String currentStep,
        String command,
        String errorCode,
        String errorMessage,
        String commitHash,
        String traceId,
        Instant occurredAt) {
}
