package com.icbc.testagent.opencode.runtime.run;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.RunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Run session scope 运行态 Redis cache。Redis 只负责 pending/dedup 增强，失败时不阻断 Run。
 */
@Component
class RunSessionScopeRuntimeCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunSessionScopeRuntimeCache.class);
    static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    RunSessionScopeRuntimeCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    static RunSessionScopeRuntimeCache disabled(ObjectMapper objectMapper) {
        return new RunSessionScopeRuntimeCache(null, objectMapper);
    }

    static RunSessionScopeRuntimeCache disabled() {
        return disabled(new ObjectMapper().findAndRegisterModules());
    }

    /**
     * 尝试占用 raw event dedup key；rawEventId 为空时调用方不应进入本方法。
     */
    boolean claimRawEvent(RunId runId, String sessionId, String rawEventId) {
        if (redisTemplate == null || rawEventId == null || rawEventId.isBlank()) {
            return true;
        }
        try {
            Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
                    dedupKey(runId, sessionId, rawEventId),
                    "1",
                    TTL);
            return !Boolean.FALSE.equals(claimed);
        } catch (RuntimeException exception) {
            LOGGER.debug(
                    "Run scope Redis dedup unavailable, runId={}, sessionId={}",
                    runId.value(),
                    sessionId,
                    exception);
            return true;
        }
    }

    /**
     * 将未知 child 事件暂存到 Redis list，等待 child 进入当前 Run scope 后 drain。
     */
    boolean appendPending(String sessionId, RunEventDraft draft) {
        if (redisTemplate == null) {
            return false;
        }
        String key = pendingKey(draft.runId(), sessionId);
        try {
            redisTemplate.opsForList().rightPush(key, writePending(draft));
            redisTemplate.expire(key, TTL);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.debug(
                    "Run scope Redis pending append unavailable, runId={}, sessionId={}",
                    draft.runId().value(),
                    sessionId,
                    exception);
            return false;
        }
    }

    /**
     * 读取并删除 pending 事件；反序列化失败的条目丢弃，DB 仍是恢复事实源。
     */
    List<RunEventDraft> drainPending(RunId runId, String sessionId) {
        if (redisTemplate == null) {
            return List.of();
        }
        String key = pendingKey(runId, sessionId);
        try {
            List<String> values = redisTemplate.opsForList().range(key, 0, -1);
            redisTemplate.delete(key);
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            return values.stream()
                    .map(this::readPending)
                    .flatMap(List::stream)
                    .toList();
        } catch (RuntimeException exception) {
            LOGGER.debug(
                    "Run scope Redis pending drain unavailable, runId={}, sessionId={}",
                    runId.value(),
                    sessionId,
                    exception);
            return List.of();
        }
    }

    private String pendingKey(RunId runId, String sessionId) {
        return "test-agent:run-scope:%s:pending:%s".formatted(runId.value(), sessionId);
    }

    private String dedupKey(RunId runId, String sessionId, String rawEventId) {
        return "test-agent:run-scope:%s:dedup:%s:%s".formatted(runId.value(), sessionId, rawEventId);
    }

    private String writePending(RunEventDraft draft) {
        try {
            return objectMapper.writeValueAsString(new PendingDraft(
                    draft.runId().value(),
                    draft.type().wireName(),
                    draft.traceId(),
                    draft.occurredAt(),
                    draft.payload()));
        } catch (Exception exception) {
            throw new IllegalStateException("Run scope pending event 序列化失败", exception);
        }
    }

    private List<RunEventDraft> readPending(String json) {
        try {
            PendingDraft pending = objectMapper.readValue(json, PendingDraft.TYPE);
            RunEventType type = RunEventType.fromWireName(pending.type())
                    .orElse(RunEventType.OPENCODE_EVENT_UNKNOWN);
            return List.of(new RunEventDraft(
                    new RunId(pending.runId()),
                    type,
                    pending.traceId(),
                    pending.occurredAt(),
                    pending.payload()));
        } catch (Exception exception) {
            LOGGER.debug("Run scope pending event 反序列化失败", exception);
            return List.of();
        }
    }

    private record PendingDraft(
            String runId,
            String type,
            String traceId,
            Instant occurredAt,
            Map<String, Object> payload) {

        private static final TypeReference<PendingDraft> TYPE = new TypeReference<>() {
        };
    }
}
