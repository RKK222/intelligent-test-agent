package com.icbc.testagent.persistence.mybatis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventId;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.event.RunEventScopeContext;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * RunEvent MyBatis Repository，负责 append-only seq 分配和结构化 scope 列的持久化。
 */
@Repository
public class MyBatisRunEventRepository implements RunEventRepository {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };
    private static final int MAX_APPEND_ATTEMPTS = 20;

    private final RunEventMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 注入 MyBatis mapper 和 JSON 编解码器，所有 SQL 维护在对应 XML 中。
     */
    public MyBatisRunEventRepository(RunEventMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 追加运行事件；并发 seq 冲突时重试，重复 rawEventId 冲突时返回已存在事件。
     */
    @Override
    public RunEvent append(RunEventDraft draft) {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        for (int attempt = 1; attempt <= MAX_APPEND_ATTEMPTS; attempt++) {
            Long nextSeq = mapper.nextSeq(draft.runId().value());
            try {
                mapper.insert(toRow(eventId, nextSeq, draft));
                return findByRunIdAndSeq(draft.runId(), nextSeq);
            } catch (DuplicateKeyException exception) {
                RunEvent duplicate = findDuplicateRawEvent(draft);
                if (duplicate != null) {
                    return duplicate;
                }
                if (attempt == MAX_APPEND_ATTEMPTS) {
                    throw exception;
                }
            }
        }
        throw new PlatformException(
                ErrorCode.INTERNAL_ERROR,
                "RunEvent seq 分配失败",
                Map.of("runId", draft.runId().value()));
    }

    /**
     * 按 lastSeq 增量读取事件流，payload 保持旧 SSE 兼容，scope 额外还原给后端使用。
     */
    @Override
    public List<RunEvent> findByRunIdAfter(RunId runId, long lastSeq, int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }
        return mapper.findByRunIdAfter(runId.value(), lastSeq, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * 按 root session 恢复跨 Run 的持久化状态事件，供 Session 级历史树使用。
     */
    @Override
    public List<RunEvent> findByRootSessionIdAfter(String rootSessionId, long lastSeq, int limit) {
        if (rootSessionId == null || rootSessionId.isBlank()) {
            return List.of();
        }
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }
        return mapper.findByRootSessionIdAfter(rootSessionId, lastSeq, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    private RunEvent findByRunIdAndSeq(RunId runId, long seq) {
        RunEventRow row = mapper.findByRunIdAndSeq(runId.value(), seq);
        if (row == null) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "RunEvent 追加后无法读取",
                    Map.of("runId", runId.value(), "seq", seq));
        }
        return toDomain(row);
    }

    private RunEvent findDuplicateRawEvent(RunEventDraft draft) {
        String rawEventId = rawEventId(draft.payload());
        RunEventScopeContext scopeContext = draft.scopeContext();
        if (rawEventId == null || scopeContext == null) {
            return null;
        }
        RunEventRow row = mapper.findByRunIdSessionIdAndRawEventId(
                draft.runId().value(),
                scopeContext.sessionId(),
                rawEventId);
        return row == null ? null : toDomain(row);
    }

    private RunEventRow toRow(String eventId, long seq, RunEventDraft draft) {
        RunEventScopeContext scopeContext = draft.scopeContext();
        return new RunEventRow(
                eventId,
                draft.runId().value(),
                seq,
                draft.type().wireName(),
                draft.traceId(),
                draft.occurredAt(),
                writePayload(draft.payload()),
                scopeContext == null ? null : scopeContext.rootSessionId(),
                scopeContext == null ? null : scopeContext.sessionId(),
                scopeContext == null ? null : scopeContext.parentSessionId(),
                scopeContext != null && scopeContext.childSession(),
                scopeContext == null ? null : scopeContext.scopeVersion(),
                scopeContext == null ? null : scopeContext.taskMessageId(),
                scopeContext == null ? null : scopeContext.taskPartId(),
                scopeContext == null ? null : scopeContext.taskCallId(),
                rawEventId(draft.payload()));
    }

    private RunEvent toDomain(RunEventRow row) {
        RunEventType type = RunEventType.fromWireName(row.type())
                .orElseThrow(() -> new IllegalStateException("unknown run event type: " + row.type()));
        return new RunEvent(
                new RunEventId(row.eventId()),
                new RunId(row.runId()),
                row.seq(),
                type,
                row.traceId(),
                row.occurredAt(),
                readPayload(row.payloadJson()),
                toScopeContext(row));
    }

    private RunEventScopeContext toScopeContext(RunEventRow row) {
        if (row.rootSessionId() == null || row.sessionId() == null || row.scopeVersion() == null) {
            return null;
        }
        return new RunEventScopeContext(
                new RunId(row.runId()),
                row.rootSessionId(),
                row.sessionId(),
                row.parentSessionId(),
                row.childSession(),
                row.taskMessageId(),
                row.taskPartId(),
                row.taskCallId(),
                row.scopeVersion(),
                true);
    }

    private String rawEventId(Map<String, Object> payload) {
        Object value = payload.get("rawEventId");
        if (value instanceof String rawEventId && !rawEventId.isBlank()) {
            return rawEventId;
        }
        return null;
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "RunEvent payload 序列化失败", Map.of(), exception);
        }
    }

    private Map<String, Object> readPayload(String json) {
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "RunEvent payload 反序列化失败", Map.of(), exception);
        }
    }
}
