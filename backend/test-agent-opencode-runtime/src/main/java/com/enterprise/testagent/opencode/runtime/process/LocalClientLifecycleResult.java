package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import java.time.Instant;

/** 客户端以 ProcessHandle 和 loopback health 实际观测得到的生命周期结果。 */
public record LocalClientLifecycleResult(
        boolean success,
        LocalClientProcessStatus processStatus,
        Long processId,
        Instant processStartedAt,
        Integer opencodePort,
        boolean opencodeHealthy,
        String executable,
        String message) {
}
