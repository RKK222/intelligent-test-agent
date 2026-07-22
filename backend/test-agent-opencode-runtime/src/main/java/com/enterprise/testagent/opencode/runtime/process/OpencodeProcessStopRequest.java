package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 公共 opencode server 停止请求，表达目标容器端口和可选平台进程记录。
 *
 * <p>带 `processId/baseUrl` 的 tracked 请求会在 manager stop 后继续执行 health 确认；
 * untracked 请求只适用于运行管理中没有平台进程记录的无主 manager state。
 */
public record OpencodeProcessStopRequest(
        OpencodeContainerId containerId,
        int port,
        OpencodeProcessId processId,
        String baseUrl,
        String traceId,
        OpencodeServerProcess processSnapshot) {

    public OpencodeProcessStopRequest {
        Objects.requireNonNull(containerId, "containerId must not be null");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (processId != null) {
            baseUrl = DomainValidation.requireText(baseUrl, "baseUrl");
            if (processSnapshot != null && !processSnapshot.processId().equals(processId)) {
                throw new IllegalArgumentException("processSnapshot must match processId");
            }
        } else if (baseUrl != null && baseUrl.isBlank()) {
            baseUrl = null;
        }
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    /**
     * 创建有平台进程记录的停止请求，停止成功前必须确认 health 不健康。
     */
    public static OpencodeProcessStopRequest tracked(OpencodeServerProcess process, String traceId) {
        Objects.requireNonNull(process, "process must not be null");
        return new OpencodeProcessStopRequest(
                process.containerId(),
                process.port(),
                process.processId(),
                process.baseUrl(),
                traceId,
                process);
    }

    /**
     * 创建无平台进程记录的停止请求，只能以 manager stop 回包作为确认依据。
     */
    public static OpencodeProcessStopRequest untracked(
            OpencodeContainerId containerId,
            int port,
            String traceId) {
        return new OpencodeProcessStopRequest(containerId, port, null, null, traceId, null);
    }

    public boolean tracked() {
        return processId != null;
    }
}
