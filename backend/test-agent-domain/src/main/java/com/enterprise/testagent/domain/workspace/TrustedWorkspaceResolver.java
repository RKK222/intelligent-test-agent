package com.enterprise.testagent.domain.workspace;

/**
 * 在当前 Java 节点解析可信工作区 root/server；历史空 server 只能由可访问真实路径的节点安全回填。
 */
public interface TrustedWorkspaceResolver {

    TrustedWorkspaceResolution resolveTrustedWorkspace(WorkspaceId workspaceId, String traceId);
}
