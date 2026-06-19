package com.example.testagent.opencode.client;

import java.util.List;

/**
 * opencode Diff 查询结果，文件列表只包含平台稳定字段。
 */
public record OpencodeDiffResult(List<OpencodeDiffFile> files) {

    public OpencodeDiffResult {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
