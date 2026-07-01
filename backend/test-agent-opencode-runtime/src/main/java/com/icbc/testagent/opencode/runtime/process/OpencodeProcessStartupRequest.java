package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * 公共 opencode server 启动请求，表达目标用户、固定端口和启动路径。
 *
 * <p>`processId`、`createdAt`、`bindingCreatedAt` 为空时表示首次分配，由启动服务生成进程 ID
 * 并使用当前时间作为创建时间；运行管理重启旧进程时应传入既有值以保持绑定稳定。
 */
public record OpencodeProcessStartupRequest(
        UserId userId,
        OpencodeProcessId processId,
        Instant createdAt,
        Instant bindingCreatedAt,
        LinuxServerId linuxServerId,
        OpencodeContainerId containerId,
        int port,
        String baseUrl,
        String sessionPath,
        String configPath,
        String traceId) {

    public OpencodeProcessStartupRequest {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        Objects.requireNonNull(containerId, "containerId must not be null");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        baseUrl = DomainValidation.requireText(baseUrl, "baseUrl");
        sessionPath = DomainValidation.requireText(sessionPath, "sessionPath");
        configPath = DomainValidation.requireText(configPath, "configPath");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
