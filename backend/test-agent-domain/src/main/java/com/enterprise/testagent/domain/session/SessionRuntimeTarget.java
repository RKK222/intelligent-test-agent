package com.enterprise.testagent.domain.session;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import java.util.Objects;

/** 会话创建时冻结的 OpenCode 运行目标；客户端离线后禁止降级到服务端进程。 */
public record SessionRuntimeTarget(
        SessionId sessionId,
        RuntimeKind runtimeKind,
        LocalClientInstanceId localClientInstanceId) {

    public SessionRuntimeTarget {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        runtimeKind = RuntimeKind.fromNullable(runtimeKind);
        if ((runtimeKind == RuntimeKind.LOCAL_CLIENT) != (localClientInstanceId != null)) {
            throw new IllegalArgumentException("session runtime kind and local client target must match");
        }
    }

    public static SessionRuntimeTarget server(SessionId sessionId) {
        return new SessionRuntimeTarget(sessionId, RuntimeKind.SERVER_PROCESS, null);
    }
}
