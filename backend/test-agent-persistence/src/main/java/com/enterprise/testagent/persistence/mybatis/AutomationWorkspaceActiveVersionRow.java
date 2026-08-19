package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** automation_workspace_active_versions 表内部行模型。 */
public record AutomationWorkspaceActiveVersionRow(
        String applicationWorkspaceId,
        String versionId,
        String activatedByUserId,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt) {
}
