package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 体验 Workspace 的 MyBatis 写入行模型。 */
public record ExperienceWorkspaceRow(
        String workspaceId,
        String name,
        String rootPath,
        String status,
        String linuxServerId,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {
}
