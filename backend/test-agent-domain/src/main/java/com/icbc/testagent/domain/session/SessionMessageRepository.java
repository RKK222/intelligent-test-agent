package com.icbc.testagent.domain.session;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import java.util.Optional;

/**
 * SessionMessage 持久化端口，应用层通过端口读写消息，不依赖 JDBC 实现。
 */
public interface SessionMessageRepository {

    /**
     * 保存平台会话消息。
     */
    SessionMessage save(SessionMessage message);

    /**
     * 按消息 ID 查询单条会话消息。
     */
    Optional<SessionMessage> findById(SessionMessageId messageId);

    /**
     * 按会话 ID 分页读取消息。
     */
    PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest);
}
