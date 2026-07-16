package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * Agent 配置 Git 长操作表行模型，对应 agent_config_operations 表。
 */
public record AgentConfigOperationRow(
        String operationId,
        String scope,
        String workspaceId,
        String action,
        String status,
        String currentStep,
        String errorCode,
        String errorMessage,
        String traceId,
        String branch,
        String commitHash,
        Instant createdAt,
        Instant updatedAt) {
}
