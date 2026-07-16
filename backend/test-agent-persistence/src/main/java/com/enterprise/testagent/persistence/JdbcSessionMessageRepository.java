package com.enterprise.testagent.persistence;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.TokenUsage;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessage;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.session.SessionMessageRepository;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.user.UserId;
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
            rs.getString("trace_id"),
            runId(rs.getString("run_id")),
            rs.getString("agent_id"),
            rs.getString("remote_message_id"),
            rs.getString("parts_json"),
            new TokenUsage(
                    rs.getObject("tokens_input", Long.class),
                    rs.getObject("tokens_output", Long.class),
                    rs.getObject("tokens_reasoning", Long.class),
                    rs.getObject("tokens_cache_read", Long.class),
                    rs.getObject("tokens_cache_write", Long.class)),
            rs.getBigDecimal("cost_usd"),
            instantOrDefault(rs, "updated_at", instant(rs, "created_at")),
            ConversationSourceType.valueOf(rs.getString("source_type")),
            rs.getString("source_ref_id"),
            userId(rs.getString("sender_user_id")));

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
                                trace_id = :traceId, created_at = :createdAt,
                                run_id = :runId, agent_id = :agentId,
                                remote_message_id = :remoteMessageId, parts_json = :partsJson,
                                tokens_input = :tokensInput, tokens_output = :tokensOutput,
                                tokens_reasoning = :tokensReasoning,
                                tokens_cache_read = :tokensCacheRead,
                                tokens_cache_write = :tokensCacheWrite,
                                cost_usd = :costUsd, updated_at = :updatedAt,
                                source_type = :sourceType,
                                source_ref_id = :sourceRefId,
                                sender_user_id = :senderUserId
                            where message_id = :messageId
                            """)
                    .param("messageId", message.messageId().value())
                    .param("sessionId", message.sessionId().value())
                    .param("role", message.role().name())
                    .param("content", message.content())
                    .param("traceId", message.traceId())
                    .param("createdAt", timestamp(message.createdAt()))
                    .param("runId", runIdValue(message.runId()))
                    .param("agentId", message.agentId())
                    .param("remoteMessageId", message.remoteMessageId())
                    .param("partsJson", message.partsJson())
                    .param("tokensInput", message.tokenUsage().input())
                    .param("tokensOutput", message.tokenUsage().output())
                    .param("tokensReasoning", message.tokenUsage().reasoning())
                    .param("tokensCacheRead", message.tokenUsage().cacheRead())
                    .param("tokensCacheWrite", message.tokenUsage().cacheWrite())
                    .param("costUsd", message.costUsd())
                    .param("updatedAt", timestamp(message.updatedAt()))
                    .param("sourceType", message.sourceType().name())
                    .param("sourceRefId", message.sourceRefId())
                    .param("senderUserId", userIdValue(message.senderUserId()))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into session_messages(
                                message_id, session_id, role, content, trace_id, created_at,
                                run_id, agent_id, remote_message_id, parts_json,
                                tokens_input, tokens_output, tokens_reasoning,
                                tokens_cache_read, tokens_cache_write, cost_usd, updated_at,
                                source_type, source_ref_id, sender_user_id
                            )
                            values (
                                :messageId, :sessionId, :role, :content, :traceId, :createdAt,
                                :runId, :agentId, :remoteMessageId, :partsJson,
                                :tokensInput, :tokensOutput, :tokensReasoning,
                                :tokensCacheRead, :tokensCacheWrite, :costUsd, :updatedAt,
                                :sourceType, :sourceRefId, :senderUserId
                            )
                            """)
                    .param("messageId", message.messageId().value())
                    .param("sessionId", message.sessionId().value())
                    .param("role", message.role().name())
                    .param("content", message.content())
                    .param("traceId", message.traceId())
                    .param("createdAt", timestamp(message.createdAt()))
                    .param("runId", runIdValue(message.runId()))
                    .param("agentId", message.agentId())
                    .param("remoteMessageId", message.remoteMessageId())
                    .param("partsJson", message.partsJson())
                    .param("tokensInput", message.tokenUsage().input())
                    .param("tokensOutput", message.tokenUsage().output())
                    .param("tokensReasoning", message.tokenUsage().reasoning())
                    .param("tokensCacheRead", message.tokenUsage().cacheRead())
                    .param("tokensCacheWrite", message.tokenUsage().cacheWrite())
                    .param("costUsd", message.costUsd())
                    .param("updatedAt", timestamp(message.updatedAt()))
                    .param("sourceType", message.sourceType().name())
                    .param("sourceRefId", message.sourceRefId())
                    .param("senderUserId", userIdValue(message.senderUserId()))
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
                        select message_id, session_id, role, content, trace_id, created_at,
                               run_id, agent_id, remote_message_id, parts_json,
                               tokens_input, tokens_output, tokens_reasoning,
                               tokens_cache_read, tokens_cache_write, cost_usd, updated_at,
                               source_type, source_ref_id, sender_user_id
                        from session_messages
                        where message_id = :messageId
                        """)
                .param("messageId", messageId.value())
                .query(rowMapper)
                .optional();
    }

    /**
     * 按远端消息 ID 查询平台快照，确保同一条 opencode message 多次恢复不会重复落库。
     */
    @Override
    public Optional<SessionMessage> findBySessionIdAndRemoteMessageId(SessionId sessionId, String remoteMessageId) {
        if (remoteMessageId == null || remoteMessageId.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
                        select message_id, session_id, role, content, trace_id, created_at,
                               run_id, agent_id, remote_message_id, parts_json,
                               tokens_input, tokens_output, tokens_reasoning,
                               tokens_cache_read, tokens_cache_write, cost_usd, updated_at,
                               source_type, source_ref_id, sender_user_id
                        from session_messages
                        where session_id = :sessionId
                          and remote_message_id = :remoteMessageId
                        order by updated_at desc, id desc
                        limit 1
                        """)
                .param("sessionId", sessionId.value())
                .param("remoteMessageId", remoteMessageId)
                .query(rowMapper)
                .optional();
    }

    /**
     * 按会话 ID 正序分页读取消息，保证前端按对话顺序展示。
     */
    @Override
    public PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest) {
        var items = jdbcClient.sql("""
                        select message_id, session_id, role, content, trace_id, created_at,
                               run_id, agent_id, remote_message_id, parts_json,
                               tokens_input, tokens_output, tokens_reasoning,
                               tokens_cache_read, tokens_cache_write, cost_usd, updated_at,
                               source_type, source_ref_id, sender_user_id
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

    private RunId runId(String value) {
        return value == null ? null : new RunId(value);
    }

    private String runIdValue(RunId runId) {
        return runId == null ? null : runId.value();
    }

    private UserId userId(String value) {
        return value == null ? null : new UserId(value);
    }

    private String userIdValue(UserId userId) {
        return userId == null ? null : userId.value();
    }
}
