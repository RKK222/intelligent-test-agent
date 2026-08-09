package com.enterprise.testagent.domain.session;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.run.RunId;
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
     * 按会话与 Run 精确读取平台 USER 消息；生产仓储必须覆盖为关系库精确查询。
     *
     * <p>默认实现仅用于兼容旧仓储和轻量测试替身，采用有界分页，避免恢复链路无界扫描。</p>
     */
    default Optional<SessionMessage> findUserBySessionIdAndRunId(SessionId sessionId, RunId runId) {
        for (int page = 1; page <= 20; page++) {
            PageResponse<SessionMessage> response = findBySessionId(
                    sessionId,
                    new PageRequest(page, PageRequest.MAX_SIZE));
            Optional<SessionMessage> match = response.items().stream()
                    .filter(message -> message.role() == SessionMessageRole.USER)
                    .filter(message -> runId.equals(message.runId()))
                    .findFirst();
            if (match.isPresent() || response.items().isEmpty()
                    || (long) page * PageRequest.MAX_SIZE >= response.total()) {
                return match;
            }
        }
        return Optional.empty();
    }

    /**
     * 按会话 ID 分页读取消息。
     */
    PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest);
}
