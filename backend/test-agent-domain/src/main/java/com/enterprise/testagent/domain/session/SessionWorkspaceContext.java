package com.enterprise.testagent.domain.session;

/**
 * 历史会话所属应用、应用工作空间模板和版本上下文；字段允许为空以兼容非托管或已删除配置。
 */
public record SessionWorkspaceContext(
        String appId,
        String appName,
        String applicationWorkspaceId,
        String workspaceName,
        String versionId,
        String version) {

    /**
     * 判断上下文是否完全缺失，便于 API 层按 null 语义返回旧数据。
     */
    public boolean empty() {
        return isBlank(appId)
                && isBlank(appName)
                && isBlank(applicationWorkspaceId)
                && isBlank(workspaceName)
                && isBlank(versionId)
                && isBlank(version);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
