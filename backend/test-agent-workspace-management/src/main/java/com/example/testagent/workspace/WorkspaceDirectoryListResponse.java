package com.example.testagent.workspace;

import java.util.List;

/**
 * 工作区目录选择器响应，包含当前目录、可回退父目录和一层子目录列表。
 */
public record WorkspaceDirectoryListResponse(
        String path,
        String parentPath,
        List<WorkspaceDirectoryEntryResponse> entries) {
}
