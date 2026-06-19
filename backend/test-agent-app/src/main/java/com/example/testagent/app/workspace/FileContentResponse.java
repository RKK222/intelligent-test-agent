package com.example.testagent.app.workspace;

/**
 * 文件内容响应 DTO，仅表示 UTF-8 文本文件读取结果。
 */
public record FileContentResponse(String path, String content, long size) {
}
