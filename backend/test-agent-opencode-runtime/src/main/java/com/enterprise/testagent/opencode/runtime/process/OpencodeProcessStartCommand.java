package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Map;
import java.util.Objects;

/**
 * 请求容器管理进程启动用户 opencode server 的命令。
 */
public record OpencodeProcessStartCommand(
        UserId userId,
        LinuxServerId linuxServerId,
        OpencodeContainerId containerId,
        int port,
        String baseUrl,
        String sessionPath,
        String configPath,
        Map<String, String> environment,
        String traceId) {

    public OpencodeProcessStartCommand {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        Objects.requireNonNull(containerId, "containerId must not be null");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        baseUrl = DomainValidation.requireText(baseUrl, "baseUrl");
        sessionPath = DomainValidation.requireText(sessionPath, "sessionPath");
        configPath = DomainValidation.requireText(configPath, "configPath");
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
