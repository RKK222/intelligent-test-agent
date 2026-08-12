package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.localclient.LocalClientModelGrant;
import com.enterprise.testagent.domain.localclient.LocalClientModelGrantStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 短期模型授权 Redis 存储；key 和 payload 都只包含 grant 摘要。 */
public class RedisLocalClientModelGrantStore implements LocalClientModelGrantStore {

    private static final String PREFIX = "test-agent:local-client:model-grant:";
    private static final String CONNECTION_PREFIX = "test-agent:local-client:model-grant-connection:";
    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            local previous = redis.call('GET', KEYS[2])
            if previous and previous ~= ARGV[1] then
              redis.call('DEL', KEYS[3] .. previous)
            end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> DELETE_CONNECTION_SCRIPT = new DefaultRedisScript<>("""
            local digest = redis.call('GET', KEYS[1])
            redis.call('DEL', KEYS[1])
            if digest then
              redis.call('DEL', KEYS[2] .. digest)
              return 1
            end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisLocalClientModelGrantStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void save(LocalClientModelGrant grant, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        redis.execute(
                SAVE_SCRIPT,
                List.of(key(grant.fingerprint()), connectionKey(grant.clientInstanceId(), grant.connectionGeneration()), PREFIX),
                grant.fingerprint(),
                write(grant),
                Long.toString(ttl.toMillis()));
    }

    @Override
    public Optional<LocalClientModelGrant> findByFingerprint(String fingerprint) {
        String json = redis.opsForValue().get(key(fingerprint));
        return json == null ? Optional.empty() : Optional.of(read(json));
    }

    @Override
    public boolean delete(String fingerprint) {
        return Boolean.TRUE.equals(redis.delete(key(fingerprint)));
    }

    @Override
    public boolean deleteForConnection(LocalClientInstanceId clientInstanceId, long connectionGeneration) {
        Long deleted = redis.execute(
                DELETE_CONNECTION_SCRIPT,
                List.of(connectionKey(clientInstanceId, connectionGeneration), PREFIX));
        return Long.valueOf(1L).equals(deleted);
    }

    private String write(LocalClientModelGrant grant) {
        try {
            return objectMapper.writeValueAsString(grant);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("local client model grant serialization failed", exception);
        }
    }

    private LocalClientModelGrant read(String json) {
        try {
            return objectMapper.readValue(json, LocalClientModelGrant.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("local client model grant deserialization failed", exception);
        }
    }

    private static String key(String fingerprint) {
        return PREFIX + fingerprint;
    }

    private static String connectionKey(LocalClientInstanceId clientInstanceId, long generation) {
        return CONNECTION_PREFIX + clientInstanceId.value() + ":" + generation;
    }
}
