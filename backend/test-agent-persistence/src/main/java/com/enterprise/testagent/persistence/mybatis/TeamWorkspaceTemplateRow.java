package com.enterprise.testagent.persistence.mybatis;

/** 团队可见工作空间模板查询行。 */
public record TeamWorkspaceTemplateRow(
        String workspaceId,
        String appId,
        String workspaceName,
        String branch,
        String directoryPath,
        boolean enabled) {
}
