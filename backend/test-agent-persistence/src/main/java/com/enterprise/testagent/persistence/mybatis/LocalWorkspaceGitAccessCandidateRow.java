package com.enterprise.testagent.persistence.mybatis;

/** 本地客户端工作空间 Git 权限巡检候选行。 */
public record LocalWorkspaceGitAccessCandidateRow(
        String userId,
        String workspaceId,
        String clientInstanceId,
        String rootDigest) {
}
