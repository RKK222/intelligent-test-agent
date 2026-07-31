package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.workflowcapability.CheckoutTicketPayload;
import com.enterprise.testagent.domain.workflowcapability.WorkflowCapabilityStore;
import com.enterprise.testagent.domain.workflowcapability.WorkflowModelGrantPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 工作流共享能力的短期Redis实现；所有票据和grant均只用SHA-256摘要寻址。 */
public class RedisWorkflowCapabilityStore implements WorkflowCapabilityStore {

    private static final String PREFIX = "test-agent:workflow-capability:";
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); if value then redis.call('DEL', KEYS[1]); end; return value;",
            String.class);
    private static final DefaultRedisScript<Long> SAVE_GRANT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[4]) == 1 then return 0; end; "
                    + "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3]); "
                    + "redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3]); "
                    + "redis.call('SADD', KEYS[3], ARGV[4]); "
                    + "redis.call('PEXPIRE', KEYS[3], ARGV[3]); return 1;",
            Long.class);
    private static final DefaultRedisScript<Long> REFRESH_GRANT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[3]) == 1 then return 0; end; "
                    + "local digest = redis.call('GET', KEYS[1]); "
                    + "if not digest then return 0; end; "
                    + "redis.call('SET', ARGV[1] .. digest, ARGV[2], 'PX', ARGV[3]); "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[3]); "
                    + "redis.call('SADD', KEYS[2], ARGV[4]); "
                    + "redis.call('PEXPIRE', KEYS[2], ARGV[3]); return 1;",
            Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisWorkflowCapabilityStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean reserveNonce(String nonceDigest, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + "nonce:" + nonceDigest, "1", ttl));
    }

    @Override
    public void saveCheckoutTicket(String ticketDigest, CheckoutTicketPayload payload, Duration ttl) {
        redis.opsForValue().set(PREFIX + "checkout:" + ticketDigest, serialize(payload), ttl);
    }

    @Override
    public Optional<CheckoutTicketPayload> consumeCheckoutTicket(String ticketDigest) {
        String json = redis.execute(CONSUME_SCRIPT, List.of(PREFIX + "checkout:" + ticketDigest));
        return deserialize(json, CheckoutTicketPayload.class);
    }

    @Override
    public boolean saveModelGrant(
            String grantId,
            String grantDigest,
            WorkflowModelGrantPayload payload,
            Duration ttl) {
        String grantKey = PREFIX + "grant:" + grantDigest;
        String idKey = PREFIX + "grant-id:" + grantId;
        String runKey = runKey(payload.taskId(), payload.runId());
        Long saved = redis.execute(
                SAVE_GRANT_SCRIPT,
                List.of(grantKey, idKey, runKey, revokedRunKey(payload.taskId(), payload.runId())),
                serialize(payload),
                grantDigest,
                Long.toString(ttl.toMillis()),
                grantId);
        return Long.valueOf(1L).equals(saved);
    }

    @Override
    public Optional<WorkflowModelGrantPayload> findModelGrant(String grantDigest) {
        return deserialize(redis.opsForValue().get(PREFIX + "grant:" + grantDigest), WorkflowModelGrantPayload.class);
    }

    @Override
    public Optional<WorkflowModelGrantPayload> findModelGrantById(String grantId) {
        String digest = redis.opsForValue().get(PREFIX + "grant-id:" + grantId);
        return digest == null ? Optional.empty() : findModelGrant(digest);
    }

    @Override
    public boolean refreshModelGrant(
            String grantId,
            WorkflowModelGrantPayload payload,
            Duration ttl) {
        Long refreshed = redis.execute(
                REFRESH_GRANT_SCRIPT,
                List.of(
                        PREFIX + "grant-id:" + grantId,
                        runKey(payload.taskId(), payload.runId()),
                        revokedRunKey(payload.taskId(), payload.runId())),
                PREFIX + "grant:",
                serialize(payload),
                Long.toString(ttl.toMillis()),
                grantId);
        return Long.valueOf(1L).equals(refreshed);
    }

    @Override
    public List<WorkflowModelGrantPayload> findModelGrants(String taskId, String runId) {
        Set<String> grantIds = redis.opsForSet().members(runKey(taskId, runId));
        if (grantIds == null || grantIds.isEmpty()) {
            return List.of();
        }
        return grantIds.stream()
                .map(this::findModelGrantById)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public void revokeModelGrant(String grantId) {
        String idKey = PREFIX + "grant-id:" + grantId;
        String digest = redis.opsForValue().get(idKey);
        if (digest == null) {
            return;
        }
        Optional<WorkflowModelGrantPayload> payload = findModelGrant(digest);
        redis.delete(List.of(idKey, PREFIX + "grant:" + digest));
        payload.ifPresent(value -> redis.opsForSet().remove(runKey(value.taskId(), value.runId()), grantId));
    }

    @Override
    public void revokeModelGrants(String taskId, String runId, Duration tombstoneTtl) {
        String runKey = runKey(taskId, runId);
        // 先写墓碑：在此命令之后到达Redis的签发Lua都会失败；此前已完成的签发
        // 已原子写入run集合，因此下面的枚举不会漏掉它。
        redis.opsForValue().set(revokedRunKey(taskId, runId), "1", tombstoneTtl);
        Set<String> grantIds = redis.opsForSet().members(runKey);
        if (grantIds != null) {
            grantIds.forEach(this::revokeModelGrant);
        }
        redis.delete(runKey);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("workflow capability serialization failed", exception);
        }
    }

    private <T> Optional<T> deserialize(String value, Class<T> type) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, type));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private static String runKey(String taskId, String runId) {
        return PREFIX + "run-grants:" + taskId + ":" + runId;
    }

    private static String revokedRunKey(String taskId, String runId) {
        return PREFIX + "run-revoked:" + taskId + ":" + runId;
    }
}
