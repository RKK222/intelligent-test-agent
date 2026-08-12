package com.enterprise.testagent.domain.workspace;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/**
 * 单台后端服务器当前生效的体验工作区绑定。
 *
 * <p>配置原值用于检测管理员变更；物理真实路径保存在关联 Workspace 中，避免在绑定表重复维护路径事实。
 */
public record ExperienceWorkspaceBinding(
        String linuxServerId,
        WorkspaceId workspaceId,
        String configuredParameterValue,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {

    public ExperienceWorkspaceBinding {
        linuxServerId = DomainValidation.requireText(linuxServerId, "linuxServerId").trim();
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        configuredParameterValue = DomainValidation.requireText(
                configuredParameterValue, "configuredParameterValue");
        traceId = DomainValidation.requireText(traceId, "traceId");
        createdAt = DomainValidation.requireInstant(createdAt, "createdAt");
        updatedAt = DomainValidation.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
