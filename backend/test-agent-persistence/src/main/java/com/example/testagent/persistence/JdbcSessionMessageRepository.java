package com.example.testagent.persistence;

import com.example.testagent.common.pagination.PageRequest;
import com.example.testagent.common.pagination.PageResponse;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.session.SessionMessage;
import com.example.testagent.domain.session.SessionMessageId;
import com.example.testagent.domain.session.SessionMessageRepository;
import com.example.testagent.domain.session.SessionMessageRole;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SessionMessage JDBC Repository，负责平台会话消息与 session_messages 表的显式映射。
 */
@Repository
public class JdbcSessionMessageRepository extends JdbcRepositorySupport implements SessionMessageRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<SessionMessage> rowMapper = (rs, rowNum) -> new SessionMessage(
            new SessionMessageId(rs.getString("message_id")),
            new SessionId(rs.getString("session_id")),
            SessionMessageRole.valueOf(rs.getString("role")),
            rs.getString("content"),
            instant(rs, "created_at"),
            rs.getString("trace_id"));

    /**
     * 注入 JdbcClient，消息持久化保持和 Session 聚合分离。
     */
    public JdbcSessionMessageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 保存会话消息；重复 messageId 时更新内容和元数据。
     */
    @Override
    public SessionMessage save(SessionMessage message) {
        if (findById(message.messageId()).isPresent()) {
            jdbcClient.sql("""
                            update session_messages
                            set session_id = :sessionId, role = :role, content = :content,
                                trace_id = :traceId, created_at = :createdAt
                            where message_id = :messageId
                            """)
                    .param("messageId", message.messageId().value())
                    .param("sessionId", message.sessionId().value())
                    .param("role", message.role().name())
                    .param("content", message.content())
                    .param("traceId", message.traceId())
                    .param("createdAt", timestamp(message.createdAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into session_messages(message_id, session_id, role, content, trace_id, created_at)
                            values (:messageId, :sessionId, :role, :content, :traceId, :createdAt)
                            """)
                    .param("messageId", message.messageId().value())
                    .param("sessionId", message.sessionId().value())
                    .param("role", message.role().name())
                    .param("content", message.content())
                    .param("traceId", message.traceId())
                    .param("createdAt", timestamp(message.createdAt()))
                    .update();
        }
        return message;
    }

    /**
     * 按消息 ID 查询单条会话消息。
     */
    @Override
    public Optional<SessionMessage> findById(SessionMessageId messageId) {
        return jdbcClient.sql("""
                        select message_id, session_id, role, content, trace_id, created_at
                        from session_messages
                        where message_id = :messageId
                        """)
                .param("messageId", messageId.value())
                .query(rowMapper)
                .optional();
    }

    /**
     * 按会话 ID 正序分页读取消息，保证前端按对话顺序展示。
     */
    @Override
    public PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest) {
        var items = jdbcClient.sql("""
                        select message_id, session_id, role, content, trace_id, created_at
                        from session_messages
                        where session_id = :sessionId
                        order by created_at asc, id asc
                        limit :limit offset :offset
                        """)
                .param("sessionId", sessionId.value())
                .param("limit", pageRequest.size())
                .param("offset", pageRequest.offset())
                .query(rowMapper)
                .list();
        Long total = jdbcClient.sql("""
                        select count(*)
                        from session_messages
                        where session_id = :sessionId
                        """)
                .param("sessionId", sessionId.value())
                .query(Long.class)
                .single();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
    }
}
