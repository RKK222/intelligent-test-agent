package com.enterprise.testagent.workspace;

import java.time.Instant;

/**
 * 文件搜索结果响应，包含相对路径、文件名、父目录路径及文件元信息。
 * 前端据此渲染搜索结果列表，在文件名中高亮关键字并展示父目录路径。
 */
public record FileSearchResultResponse(
    String path,
    String name,
    String directory,
    long size,
    Instant lastModifiedAt
) {}