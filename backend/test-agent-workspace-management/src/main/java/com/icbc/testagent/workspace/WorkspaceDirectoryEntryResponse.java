package com.icbc.testagent.workspace;

/**
 * 工作区目录选择器的单个可选目录项，只暴露目录名称和可继续选择的绝对路径。
 */
public record WorkspaceDirectoryEntryResponse(String name, String path) {
}
