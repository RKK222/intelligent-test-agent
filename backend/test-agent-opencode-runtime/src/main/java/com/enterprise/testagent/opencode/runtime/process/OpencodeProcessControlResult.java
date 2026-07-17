package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.support.DomainValidation;

/**
 * 管理进程控制命令的同步响应，用于运行管理只读页面展示操作结果。
 */
public record OpencodeProcessControlResult(
        String command,
        String status,
        int port,
        Long pid,
        String baseUrl,
        String sessionPath,
        String configPath,
        Boolean healthy,
        String message,
        String traceId) {

    public OpencodeProcessControlResult {
        command = DomainValidation.requireText(command, "command");
        status = DomainValidation.requireText(status, "status");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
