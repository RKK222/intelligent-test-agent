package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 单台服务器当前体验绑定的 MyBatis 行模型。 */
public record ExperienceWorkspaceBindingRow(
        String linuxServerId,
        String workspaceId,
        String configuredParameterValue,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {
}
