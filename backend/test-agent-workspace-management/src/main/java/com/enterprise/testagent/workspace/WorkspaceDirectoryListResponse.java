package com.enterprise.testagent.workspace;

import java.util.List;

/**
 * 服务器工作空间目录选择器响应，包含当前目录、可回退父目录和一层子目录列表。
 */
public record WorkspaceDirectoryListResponse(
        String path,
        String parentPath,
        List<WorkspaceDirectoryEntryResponse> entries) {
}
