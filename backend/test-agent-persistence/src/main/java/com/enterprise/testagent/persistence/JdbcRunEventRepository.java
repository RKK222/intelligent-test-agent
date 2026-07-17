package com.enterprise.testagent.persistence;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.event.RunEvent;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventId;
import com.enterprise.testagent.domain.event.RunEventRepository;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.run.RunId;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * RunEvent JDBC Repository，集中维护 append-only 的 eventId 和同一 run 内 seq 分配规则。
 */
public class JdbcRunEventRepository extends JdbcRepositorySupport implements RunEventRepository {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RowMapper<RunEvent> rowMapper = (rs, rowNum) -> {
        String type = rs.getString("type");
        return new RunEvent(
                new RunEventId(rs.getString("event_id")),
                new RunId(rs.getString("run_id")),
                rs.getLong("seq"),
                RunEventType.fromWireName(type)
                        .orElseThrow(() -> new IllegalStateException("unknown run event type: " + type)),
                rs.getString("trace_id"),
                instant(rs, "occurred_at"),
                readPayload(rs.getString("payload_json")));
    };

    /**
     * 注入 JdbcClient 和 ObjectMapper，payload JSON 转换集中在本类处理。
     */
    public JdbcRunEventRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 追加运行事件，并在唯一约束冲突时重试分配同一 run 内单调递增的 seq。
     */
    @Override
    public RunEvent append(RunEventDraft draft) {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        for (int attempt = 1; attempt <= 20; attempt++) {
            Long nextSeq = nextSeq(draft.runId());
            try {
                jdbcClient.sql("""
                                insert into run_events(event_id, run_id, seq, type, trace_id, occurred_at, payload_json)
                                values (:eventId, :runId, :seq, :type, :traceId, :occurredAt, :payloadJson)
                                """)
                        .param("eventId", eventId)
                        .param("runId", draft.runId().value())
                        .param("seq", nextSeq)
                        .param("type", draft.type().wireName())
                        .param("traceId", draft.traceId())
                        .param("occurredAt", timestamp(draft.occurredAt()))
                        .param("payloadJson", writePayload(draft.payload()))
                        .update();

                return findByRunIdAndSeq(draft.runId(), nextSeq);
            } catch (DuplicateKeyException exception) {
                // 并发 append 可能读到相同 max(seq)，唯一约束冲突后重读即可保持 append-only 顺序。
                if (attempt == 20) {
                    throw exception;
                }
            }
        }
        throw new PlatformException(ErrorCode.INTERNAL_ERROR, "RunEvent seq 分配失败", Map.of("runId", draft.runId().value()));
    }

    /**
     * 读取同一 run 当前最大 seq 并分配下一个序号。
     */
    private Long nextSeq(RunId runId) {
        return jdbcClient.sql("""
                        select coalesce(max(seq), 0) + 1
                        from run_events
                        where run_id = :runId
                        """)
                .param("runId", runId.value())
                .query(Long.class)
                .single();
    }

    /**
     * 按 lastSeq 增量读取事件流，limit 限制用于保护轮询和 SSE 恢复查询。
     */
    @Override
    public List<RunEvent> findByRunIdAfter(RunId runId, long lastSeq, int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }
        return jdbcClient.sql("""
                        select event_id, run_id, seq, type, trace_id, occurred_at, payload_json
                        from run_events
                        where run_id = :runId and seq > :lastSeq
                        order by seq asc
                        limit :limit
                        """)
                .param("runId", runId.value())
                .param("lastSeq", lastSeq)
                .param("limit", limit)
                .query(rowMapper)
                .list();
    }

    /**
     * append 成功后按 runId/seq 读回完整领域事件，确保返回数据库实际持久化结果。
     */
    private RunEvent findByRunIdAndSeq(RunId runId, long seq) {
        return jdbcClient.sql("""
                        select event_id, run_id, seq, type, trace_id, occurred_at, payload_json
                        from run_events
                        where run_id = :runId and seq = :seq
                        """)
                .param("runId", runId.value())
                .param("seq", seq)
                .query(rowMapper)
                .optional()
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "RunEvent 追加后无法读取",
                        Map.of("runId", runId.value(), "seq", seq)));
    }

    /**
     * 序列化事件 payload，失败时转换为统一平台异常。
     */
    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "RunEvent payload 序列化失败", Map.of(), exception);
        }
    }

    /**
     * 反序列化事件 payload，读取失败说明数据库内容不符合当前契约。
     */
    private Map<String, Object> readPayload(String json) {
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "RunEvent payload 反序列化失败", Map.of(), exception);
        }
    }
}
