package com.enterprise.testagent.persistence.mybatis;

/** 应用工作空间 Git 权限巡检候选行。 */
public record ApplicationWorkspaceGitAccessCandidateRow(
        String userId,
        String applicationWorkspaceId,
        String versionId) {
}
