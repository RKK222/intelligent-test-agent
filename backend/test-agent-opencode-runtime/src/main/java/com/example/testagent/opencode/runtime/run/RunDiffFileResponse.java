package com.example.testagent.opencode.runtime.run;

/**
 * Run Diff 中单个文件的应用层响应对象。
 */
public record RunDiffFileResponse(
        String path,
        String patch,
        long additions,
        long deletions,
        String status) {
}
