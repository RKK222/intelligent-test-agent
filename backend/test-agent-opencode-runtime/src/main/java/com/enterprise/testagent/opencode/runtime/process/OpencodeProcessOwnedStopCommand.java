package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * 带受管实例所有权栅栏的停止命令；manager 必须同时匹配统一认证号和 PID 后才能停止。
 */
public record OpencodeProcessOwnedStopCommand(
        OpencodeContainerId containerId,
        int port,
        String expectedUnifiedAuthId,
        long expectedPid,
        String traceId) {

    public OpencodeProcessOwnedStopCommand {
        Objects.requireNonNull(containerId, "containerId must not be null");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        expectedUnifiedAuthId = DomainValidation.requireText(
                expectedUnifiedAuthId,
                "expectedUnifiedAuthId");
        if (expectedPid < 1) {
            throw new IllegalArgumentException("expectedPid must be positive");
        }
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
