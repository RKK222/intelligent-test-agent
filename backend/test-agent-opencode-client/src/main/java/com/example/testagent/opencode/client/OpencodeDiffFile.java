package com.example.testagent.opencode.client;

import com.example.testagent.domain.support.DomainValidation;

/**
 * 平台内部 Diff 文件 DTO，避免 generated SDK 的 SnapshotFileDiff 泄露到业务层。
 */
public record OpencodeDiffFile(
        String path,
        String patch,
        long additions,
        long deletions,
        String status) {

    /**
     * 校验 Diff 文件路径和计数字段，patch 缺失时使用空字符串、状态缺失时使用 modified。
     */
    public OpencodeDiffFile {
        path = DomainValidation.requireText(path, "path");
        patch = patch == null ? "" : patch;
        if (additions < 0) {
            throw new IllegalArgumentException("additions must not be negative");
        }
        if (deletions < 0) {
            throw new IllegalArgumentException("deletions must not be negative");
        }
        status = status == null || status.isBlank() ? "modified" : status;
    }
}
