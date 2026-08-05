package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 用户关联工作区 MyBatis 行模型。 */
public record UserWorkspaceRow(
        String workspaceId,
        String name,
        String rootPath,
        String status,
        String linuxServerId,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {
}
