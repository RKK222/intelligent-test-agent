package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.memory.MemoryModelGrantPayload;
import com.enterprise.testagent.domain.memory.MemoryModelGrantStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** `mfg_` 一次性授权 Redis 实现；原始 Token 从不进入 key、value 或日志。 */
public class RedisMemoryModelGrantStore implements MemoryModelGrantStore {
    private static final String PREFIX = "test-agent:qa-memory:model-grant:";
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); if value then redis.call('DEL', KEYS[1]); end; return value;",
            String.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisMemoryModelGrantStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean save(String grantDigest, MemoryModelGrantPayload payload, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(grantDigest), write(payload), ttl));
    }

    @Override
    public Optional<MemoryModelGrantPayload> consume(String grantDigest) {
        String value = redis.execute(CONSUME, List.of(key(grantDigest)));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, MemoryModelGrantPayload.class));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private String write(MemoryModelGrantPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("memory model grant serialization failed", exception);
        }
    }

    private String key(String digest) {
        if (digest == null || !DIGEST.matcher(digest).matches()) {
            throw new IllegalArgumentException("grant digest must be lowercase SHA-256");
        }
        return PREFIX + digest;
    }
}
