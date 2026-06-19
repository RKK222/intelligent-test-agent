package com.example.testagent.domain.session;

import com.example.testagent.common.pagination.PageRequest;
import com.example.testagent.common.pagination.PageResponse;
import java.util.Optional;

/**
 * SessionMessage 持久化端口，应用层通过端口读写消息，不依赖 JDBC 实现。
 */
public interface SessionMessageRepository {

    SessionMessage save(SessionMessage message);

    Optional<SessionMessage> findById(SessionMessageId messageId);

    PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest);
}
