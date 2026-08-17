package com.enterprise.testagent.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.analytics.AnalyticsEventOutboxRepository;
import com.enterprise.testagent.domain.analytics.AnalyticsRuntimeOutboxReader;
import com.enterprise.testagent.persistence.mybatis.AnalyticsEventOutboxMapper;
import com.enterprise.testagent.persistence.mybatis.AnalyticsRedisOutboxRunRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** 逐个 Run 读取同槽 Redis 运营 stream，并使用 PostgreSQL 检查点保证可恢复消费。 */
@Repository
@ConditionalOnProperty(name = "test-agent.analytics.clickhouse.enabled", havingValue = "true")
public class RedisAnalyticsRuntimeOutboxReader implements AnalyticsRuntimeOutboxReader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final StringRedisTemplate redis;
    private final AnalyticsEventOutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public RedisAnalyticsRuntimeOutboxReader(
            StringRedisTemplate redis,
            AnalyticsEventOutboxMapper mapper,
            ObjectMapper objectMapper) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<RunBatch> read(int runLimit, int eventLimit, Instant recentTerminalThreshold) {
        List<RunBatch> batches = new ArrayList<>();
        for (AnalyticsRedisOutboxRunRow run : mapper.redisCandidates(recentTerminalThreshold, runLimit)) {
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().range(
                    streamKey(run.runId()),
                    Range.open(run.lastStreamId(), "+"),
                    Limit.limit().count(eventLimit));
            if (records == null || records.isEmpty()) {
                continue;
            }
            List<AnalyticsEventOutboxRepository.Event> events = records.stream()
                    .map(record -> toEvent(run, record))
                    .toList();
            batches.add(new RunBatch(run.runId(), records.getLast().getId().getValue(), events));
        }
        return batches;
    }

    @Override
    public void acknowledge(List<RunBatch> batches, Instant acknowledgedAt) {
        for (RunBatch batch : batches) {
            if (mapper.updateRedisCheckpoint(batch.runId(), batch.lastStreamId(), acknowledgedAt) > 0) {
                continue;
            }
            try {
                mapper.insertRedisCheckpoint(batch.runId(), batch.lastStreamId(), acknowledgedAt);
            } catch (DuplicateKeyException exception) {
                mapper.updateRedisCheckpoint(batch.runId(), batch.lastStreamId(), acknowledgedAt);
            }
        }
    }

    private AnalyticsEventOutboxRepository.Event toEvent(
            AnalyticsRedisOutboxRunRow run,
            MapRecord<String, Object, Object> record) {
        try {
            Object rawEvent = record.getValue().get("event");
            Map<String, Object> payload = objectMapper.readValue(String.valueOf(rawEvent), MAP_TYPE);
            Map<String, Object> enriched = new LinkedHashMap<>(payload);
            enriched.put("userId", run.userId());
            enriched.put("username", run.username());
            enriched.put("organization", run.organization());
            enriched.put("rdDepartment", run.rdDepartment());
            enriched.put("department", run.department());
            enriched.putIfAbsent("sessionId", run.sessionId());
            enriched.put("workspaceId", run.workspaceId());
            enriched.put("agentId", run.agentId());
            enriched.put("modelId", run.modelId());
            String eventType = String.valueOf(enriched.get("eventType"));
            if (eventType.startsWith("RUN_")) {
                enriched.put("capabilityName", run.agentId());
                enriched.put("status", runStatus(eventType));
            }
            enriched.put("attributionMode", "REDIS_CONSUMPTION_SNAPSHOT");
            Instant occurredAt = Instant.parse(String.valueOf(payload.get("occurredAt")));
            long version = Math.max(1L, occurredAt.toEpochMilli());
            return new AnalyticsEventOutboxRepository.Event(
                    -1L,
                    "redis:" + run.runId() + ":" + record.getId().getValue(),
                    version,
                    "REDIS_SUMMARY",
                    run.runId(),
                    occurredAt,
                    objectMapper.writeValueAsString(enriched));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new IllegalStateException("解析 Redis 运营事件失败", exception);
        }
    }

    private String streamKey(String runId) {
        return "test-agent:run:{" + runId + "}:analytics-outbox";
    }

    private String runStatus(String eventType) {
        return switch (eventType) {
            case "RUN_FAILED" -> "FAILED";
            case "RUN_CANCELLED" -> "CANCELLED";
            case "RUN_SUCCEEDED" -> "SUCCEEDED";
            default -> "STARTED";
        };
    }
}
