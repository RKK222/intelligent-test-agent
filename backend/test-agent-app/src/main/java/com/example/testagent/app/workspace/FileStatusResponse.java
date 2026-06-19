package com.example.testagent.app.workspace;

import java.time.Instant;

/**
 * 文件状态响应 DTO，初版不承载 Git 状态，只描述文件系统基础状态。
 */
public record FileStatusResponse(
        String path,
        boolean exists,
        boolean directory,
        long size,
        Instant lastModifiedAt) {
}
