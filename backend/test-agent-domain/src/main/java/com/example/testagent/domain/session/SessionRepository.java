package com.example.testagent.domain.session;

import java.util.Optional;

/**
 * Session 持久化端口，避免应用层直接依赖具体 JDBC Repository。
 */
public interface SessionRepository {

    Session save(Session session);

    Optional<Session> findById(SessionId sessionId);
}
