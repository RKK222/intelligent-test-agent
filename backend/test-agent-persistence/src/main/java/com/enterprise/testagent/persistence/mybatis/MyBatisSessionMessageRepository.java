package com.enterprise.testagent.persistence.mybatis;

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
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 会话消息生产仓储；所有新增归因字段均通过 MyBatis XML 显式读写。 */
@Repository
public class MyBatisSessionMessageRepository implements SessionMessageRepository {

    private final SessionMessageMapper mapper;

    public MyBatisSessionMessageRepository(SessionMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public SessionMessage save(SessionMessage message) {
        SessionMessageRow row = toRow(message);
        if (mapper.findById(message.messageId().value()) == null) {
            mapper.insert(row);
        } else {
            mapper.update(row);
        }
        return message;
    }

    @Override
    public Optional<SessionMessage> findById(SessionMessageId messageId) {
        return Optional.ofNullable(mapper.findById(messageId.value())).map(this::toDomain);
    }

    @Override
    public Optional<SessionMessage> findBySessionIdAndRemoteMessageId(
            SessionId sessionId,
            String remoteMessageId) {
        if (remoteMessageId == null || remoteMessageId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findBySessionAndRemoteMessage(
                        sessionId.value(), remoteMessageId))
                .map(this::toDomain);
    }

    /** 使用现有 session/run 组合索引精确读取 Run 对应的平台 USER 消息。 */
    @Override
    public Optional<SessionMessage> findUserBySessionIdAndRunId(SessionId sessionId, RunId runId) {
        return Optional.ofNullable(mapper.findUserBySessionAndRun(sessionId.value(), runId.value()))
                .map(this::toDomain);
    }

    /** 同一 Run 的消息按稳定时间顺序返回，取消或失败恢复无需扫描整段会话历史。 */
    @Override
    public List<SessionMessage> findBySessionIdAndRunId(SessionId sessionId, RunId runId) {
        return mapper.findBySessionAndRun(sessionId.value(), runId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest) {
        var items = mapper.findBySession(
                        sessionId.value(), pageRequest.size(), pageRequest.offset()).stream()
                .map(this::toDomain)
                .toList();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(),
                mapper.countBySession(sessionId.value()));
    }

    private SessionMessage toDomain(SessionMessageRow row) {
        return new SessionMessage(
                new SessionMessageId(row.messageId()),
                new SessionId(row.sessionId()),
                SessionMessageRole.valueOf(row.role()),
                row.content(),
                row.createdAt(),
                row.traceId(),
                row.runId() == null ? null : new RunId(row.runId()),
                row.agentId(),
                row.remoteMessageId(),
                row.partsJson(),
                new TokenUsage(row.tokensInput(), row.tokensOutput(), row.tokensReasoning(),
                        row.tokensCacheRead(), row.tokensCacheWrite()),
                row.costUsd(),
                row.updatedAt(),
                ConversationSourceType.valueOf(row.sourceType()),
                row.sourceRefId(),
                userId(row.senderUserId()),
                row.senderUnifiedAuthId(),
                Boolean.TRUE.equals(row.sentBySharedUser()));
    }

    private SessionMessageRow toRow(SessionMessage message) {
        return new SessionMessageRow(
                message.messageId().value(), message.sessionId().value(), message.role().name(),
                message.content(), message.traceId(), message.createdAt(), value(message.runId()),
                message.agentId(), message.remoteMessageId(), message.partsJson(),
                message.tokenUsage().input(), message.tokenUsage().output(),
                message.tokenUsage().reasoning(), message.tokenUsage().cacheRead(),
                message.tokenUsage().cacheWrite(), message.costUsd(), message.updatedAt(),
                message.sourceType().name(), message.sourceRefId(), value(message.senderUserId()),
                message.senderUnifiedAuthId(), message.sentBySharedUser());
    }

    private static String value(RunId value) {
        return value == null ? null : value.value();
    }

    private static String value(UserId value) {
        return value == null ? null : value.value();
    }

    private static UserId userId(String value) {
        return value == null ? null : new UserId(value);
    }
}
