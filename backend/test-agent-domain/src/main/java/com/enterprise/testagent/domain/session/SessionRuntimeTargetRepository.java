package com.enterprise.testagent.domain.session;

import java.util.Optional;

/** 会话运行目标使用独立 MyBatis 端口维护，避免继续扩展存量 JDBC Session SQL。 */
public interface SessionRuntimeTargetRepository {

    Optional<SessionRuntimeTarget> findBySessionId(SessionId sessionId);

    void save(SessionRuntimeTarget target);
}
