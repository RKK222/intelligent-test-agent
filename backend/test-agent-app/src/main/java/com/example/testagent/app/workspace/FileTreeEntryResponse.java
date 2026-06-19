package com.example.testagent.app.workspace;

import java.time.Instant;

/**
 * 单层目录列表项 DTO，避免目录 API 一次性递归扫描大工作区。
 */
public record FileTreeEntryResponse(
        String path,
        String name,
        boolean directory,
        long size,
        Instant lastModifiedAt) {
}
