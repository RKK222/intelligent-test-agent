package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.supportaccess.SupportAccessGrantSession;
import com.enterprise.testagent.domain.supportaccess.SupportAccessGrantStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 排查授权 Redis store。session key 仅保存当前 Token 摘要，旧 Token 即使尚未自然过期也会校验失败。
 */
public class RedisSupportAccessGrantStore implements SupportAccessGrantStore {

    private static final String PREFIX = "test-agent:support-access:";
    private static final DefaultRedisScript<String> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local previous = redis.call('GET', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])
            return previous
            """, String.class);
    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
              redis.call('DEL', KEYS[1])
            end
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisSupportAccessGrantStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Optional<SupportAccessGrantSession> rotate(SupportAccessGrantSession payload, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        String previousDigest = redis.execute(
                ROTATE_SCRIPT,
                List.of(sessionKey(payload.sessionDigest()), tokenKey(payload.grantTokenDigest())),
                payload.grantTokenDigest(),
                write(payload),
                Long.toString(ttl.toMillis()));
        if (previousDigest == null || previousDigest.isBlank()) {
            return Optional.empty();
        }
        String previousJson = redis.opsForValue().get(tokenKey(previousDigest));
        return previousJson == null ? Optional.empty() : Optional.of(read(previousJson));
    }

    @Override
    public Optional<SupportAccessGrantSession> findByTokenDigest(String grantTokenDigest) {
        String json = redis.opsForValue().get(tokenKey(grantTokenDigest));
        if (json == null) {
            return Optional.empty();
        }
        SupportAccessGrantSession payload = read(json);
        String currentDigest = redis.opsForValue().get(sessionKey(payload.sessionDigest()));
        return payload.grantTokenDigest().equals(currentDigest) ? Optional.of(payload) : Optional.empty();
    }

    @Override
    public void revoke(SupportAccessGrantSession payload) {
        redis.execute(
                REVOKE_SCRIPT,
                List.of(sessionKey(payload.sessionDigest()), tokenKey(payload.grantTokenDigest())),
                payload.grantTokenDigest());
    }

    private String write(SupportAccessGrantSession payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("support access payload serialization failed", exception);
        }
    }

    private SupportAccessGrantSession read(String json) {
        try {
            return objectMapper.readValue(json, SupportAccessGrantSession.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("support access payload deserialization failed", exception);
        }
    }

    private String sessionKey(String sessionDigest) {
        return PREFIX + "session:" + sessionDigest;
    }

    private String tokenKey(String grantTokenDigest) {
        return PREFIX + "token:" + grantTokenDigest;
    }
}
