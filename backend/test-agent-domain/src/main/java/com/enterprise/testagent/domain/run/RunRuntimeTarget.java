package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import java.util.Objects;

/** PostgreSQL Run 锚点冻结的运行目标；历史记录缺失时按服务器进程解释。 */
public record RunRuntimeTarget(
        RunId runId,
        RuntimeKind runtimeKind,
        LocalClientInstanceId localClientInstanceId) {

    public RunRuntimeTarget {
        Objects.requireNonNull(runId, "runId must not be null");
        runtimeKind = RuntimeKind.fromNullable(runtimeKind);
        if ((runtimeKind == RuntimeKind.LOCAL_CLIENT) != (localClientInstanceId != null)) {
            throw new IllegalArgumentException("run runtime kind and local client target must match");
        }
    }
}
