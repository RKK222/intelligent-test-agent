package com.enterprise.testagent.domain.session;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.util.Optional;

/** 会话运行目标使用独立 MyBatis 端口维护，避免继续扩展存量 JDBC Session SQL。 */
public interface SessionRuntimeTargetRepository {

    Optional<SessionRuntimeTarget> findBySessionId(SessionId sessionId);

    void save(SessionRuntimeTarget target);

    /** 工作区经真实目录校验接管后，同步迁移该工作区冻结的本地会话目标。 */
    int rebindLocalClientTargets(
            WorkspaceId workspaceId,
            LocalClientInstanceId expectedClientInstanceId,
            LocalClientInstanceId replacementClientInstanceId);
}
