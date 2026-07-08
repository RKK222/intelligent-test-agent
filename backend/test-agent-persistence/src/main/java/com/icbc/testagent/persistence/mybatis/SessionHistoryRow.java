package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 用户历史会话列表查询行，包含 Session 字段和可空的应用工作空间上下文。
 */
public record SessionHistoryRow(
        String sessionId,
        String workspaceId,
        String title,
        String status,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        String opencodeSessionId,
        String opencodeExecutionNodeId,
        Boolean pinned,
        String sourceType,
        String sourceRefId,
        String createdByUserId,
        String appId,
        String appName,
        String applicationWorkspaceId,
        String workspaceName,
        String versionId,
        String version) {
}
