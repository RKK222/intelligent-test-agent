package com.enterprise.testagent.workspace;

/** 工作区组合视图 UTF-8 文件读取响应。 */
public record WorkspaceViewReadResponse(
        String path,
        String content,
        long size,
        boolean readonly,
        WorkspaceViewSource source,
        String referenceAlias,
        WorkspaceViewLocator locator) {
}
