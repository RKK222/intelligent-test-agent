package com.enterprise.testagent.persistence.mybatis;

/** 数据库错配扫描结果。 */
public record PersonalWorkspaceRelocationCandidateRow(
        String personalWorkspaceId,
        String appWorkspaceVersionId,
        String userId,
        String runtimeWorkspaceId,
        String sourceLinuxServerId,
        String targetLinuxServerId) {
}
