package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * opencode_process_start_operations 表行模型，只在 persistence 模块内部使用。
 */
public record OpencodeProcessStartOperationRow(
        String operationId,
        String requestedByUserId,
        String agentId,
        String status,
        String currentStep,
        String errorCode,
        String errorMessage,
        String processId,
        String serviceAddress,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {
}
