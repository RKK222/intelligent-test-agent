package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 工作空间 Git 权限巡检结果行。 */
public record WorkspaceGitAccessCheckRow(
        String userId,
        String targetId,
        String status,
        String reason,
        String message,
        Instant checkedAt) {
}
