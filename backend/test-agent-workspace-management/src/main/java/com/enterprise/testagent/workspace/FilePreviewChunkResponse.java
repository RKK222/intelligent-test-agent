package com.enterprise.testagent.workspace;

/**
 * 大文件渐进预览分段；offset/nextOffset 使用 UTF-8 字节偏移，服务端保证每段都在字符边界结束。
 */
public record FilePreviewChunkResponse(
        String path,
        String content,
        long offset,
        long nextOffset,
        long size,
        boolean eof,
        long warningThresholdBytes,
        long lastModifiedMillis) {
}
