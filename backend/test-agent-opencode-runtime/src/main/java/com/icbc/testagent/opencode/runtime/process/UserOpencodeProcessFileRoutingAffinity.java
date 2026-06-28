package com.icbc.testagent.opencode.runtime.process;

import java.time.Instant;

/**
 * 文件 WebSocket 路由专用的用户进程服务器归属结果。
 *
 * <p>该模型只表达“文件操作应路由到哪台服务器”，不代表 opencode server
 * 健康可用，调用方不得据此放开 Run/初始化等强依赖 opencode 进程的链路。
 */
public record UserOpencodeProcessFileRoutingAffinity(
        UserOpencodeProcessAvailability status,
        boolean initializable,
        String message,
        String processId,
        String linuxServerId,
        String containerId,
        Integer port,
        String serviceAddress,
        Instant checkedAt) {

    /**
     * 兼容调用方遗漏 serviceAddress 的情况，优先由服务器 ID 与端口派生展示地址。
     */
    public UserOpencodeProcessFileRoutingAffinity {
        if ((serviceAddress == null || serviceAddress.isBlank()) && linuxServerId != null && port != null) {
            serviceAddress = linuxServerId + ":" + port;
        }
    }
}
