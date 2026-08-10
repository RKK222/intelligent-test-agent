package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/**
 * 用户历史会话列表查询行，包含 Session 字段和可空的应用工作空间上下文。
 *
 * <p>{@code shareStatus} 为会话协作分享的派生状态：未分享为 {@code null}，
 * 已分享为 {@code ACTIVE}/{@code EXPIRED}/{@code REVOKED}，由 session_shares 左连接计算。
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
        String version,
        String shareStatus) {
}
