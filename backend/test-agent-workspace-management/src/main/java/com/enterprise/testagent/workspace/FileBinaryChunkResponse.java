package com.enterprise.testagent.workspace;

/**
 * 文件原始字节下载分段；contentBase64 只承载当前有界分片，offset/nextOffset 使用原始字节偏移。
 */
public record FileBinaryChunkResponse(
        String path,
        String contentBase64,
        long offset,
        long nextOffset,
        long size,
        boolean eof,
        long lastModifiedMillis) {
}
