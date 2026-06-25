package com.icbc.testagent.opencode.runtime.process;

import java.time.Instant;

/**
 * 当前用户 opencode 进程状态响应模型，API 层可直接转换为 HTTP DTO。
 */
public record UserOpencodeProcessStatusResponse(
        UserOpencodeProcessAvailability status,
        boolean initializable,
        String message,
        String processId,
        String linuxServerId,
        String containerId,
        Integer port,
        String baseUrl,
        Instant checkedAt) {
}
