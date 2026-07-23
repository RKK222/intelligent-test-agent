package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 请求管理进程检测用户 opencode server 进程健康状态的命令。
 */
public record OpencodeProcessHealthCommand(
        OpencodeProcessId processId,
        OpencodeContainerId containerId,
        Integer port,
        String baseUrl,
        String traceId) {

    public OpencodeProcessHealthCommand {
        Objects.requireNonNull(processId, "processId must not be null");
        if (containerId == null && port != null || containerId != null && port == null) {
            throw new IllegalArgumentException("containerId and port must be provided together");
        }
        if (port != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        baseUrl = DomainValidation.requireText(baseUrl, "baseUrl");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    /** 兼容既有调用；生产状态服务始终携带精确容器和端口。 */
    public OpencodeProcessHealthCommand(
            OpencodeProcessId processId,
            String baseUrl,
            String traceId) {
        this(processId, null, null, baseUrl, traceId);
    }
}
