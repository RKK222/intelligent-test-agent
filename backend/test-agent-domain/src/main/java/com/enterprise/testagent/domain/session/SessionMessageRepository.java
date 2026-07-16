package com.enterprise.testagent.domain.session;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
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
     * 按远端消息 ID 查询快照，用于 opencode 投影刷新时做幂等 upsert。
     */
    default Optional<SessionMessage> findBySessionIdAndRemoteMessageId(SessionId sessionId, String remoteMessageId) {
        return Optional.empty();
    }

    /**
     * 按会话 ID 分页读取消息。
     */
    PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest);
}
