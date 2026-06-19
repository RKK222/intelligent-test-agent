package com.example.testagent.persistence;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.event.RunEvent;
import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.event.RunEventId;
import com.example.testagent.domain.event.RunEventRepository;
import com.example.testagent.domain.event.RunEventType;
import com.example.testagent.domain.run.RunId;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * RunEvent JDBC Repository，集中维护 append-only 的 eventId 和同一 run 内 seq 分配规则。
 */
@Repository
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

    public JdbcRunEventRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public RunEvent append(RunEventDraft draft) {
        Long nextSeq = jdbcClient.sql("""
                        select coalesce(max(seq), 0) + 1
                        from run_events
                        where run_id = :runId
                        """)
                .param("runId", draft.runId().value())
                .query(Long.class)
                .single();
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");

        jdbcClient.sql("""
                        insert into run_events(event_id, run_id, seq, type, trace_id, occurred_at, payload_json)
                        values (:eventId, :runId, :seq, :type, :traceId, :occurredAt, :payloadJson)
                        """)
                .param("eventId", eventId)
                .param("runId", draft.runId().value())
                .param("seq", nextSeq)
                .param("type", draft.type().wireName())
                .param("traceId", draft.traceId())
                .param("occurredAt", draft.occurredAt())
                .param("payloadJson", writePayload(draft.payload()))
                .update();

        return findByRunIdAndSeq(draft.runId(), nextSeq);
    }

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
