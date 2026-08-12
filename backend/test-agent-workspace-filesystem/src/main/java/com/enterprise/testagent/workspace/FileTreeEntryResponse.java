package com.enterprise.testagent.workspace;

import java.time.Instant;

/**
 * 单层目录列表项 DTO，避免目录 API 一次性递归扫描大工作区。
 */
public record FileTreeEntryResponse(
        String path,
        String name,
        boolean directory,
        long size,
        Instant lastModifiedAt,
        String displayName,
        String displayNameEn) {

    /** 普通工作区文件沿用原始名称；仅 Agent 配置列表按需补充双语展示名。 */
    public FileTreeEntryResponse(
            String path,
            String name,
            boolean directory,
            long size,
            Instant lastModifiedAt) {
        this(path, name, directory, size, lastModifiedAt, null, null);
    }

    public FileTreeEntryResponse withDisplayNames(String displayName, String displayNameEn) {
        return new FileTreeEntryResponse(path, name, directory, size, lastModifiedAt, displayName, displayNameEn);
    }
}
