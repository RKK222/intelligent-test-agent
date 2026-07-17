package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 按容器和端口向管理进程发送用户 opencode server 控制命令。
 */
public record OpencodeProcessControlCommand(
        OpencodeContainerId containerId,
        int port,
        String traceId) {

    public OpencodeProcessControlCommand {
        Objects.requireNonNull(containerId, "containerId must not be null");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
