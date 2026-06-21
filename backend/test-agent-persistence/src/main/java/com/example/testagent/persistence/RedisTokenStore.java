package com.example.testagent.persistence;

import com.example.testagent.domain.auth.AuthPrincipal;
import com.example.testagent.domain.auth.TokenStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis Token 存储，使用 StringRedisTemplate 将认证信息序列化为 JSON 存储。
 * Key 格式：{@code test-agent:token:{token}}
 * TTL：与 AuthPrincipal.expiresAt 一致（1天）
 */
public class RedisTokenStore implements TokenStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisTokenStore.class);

    private static final String KEY_PREFIX = "test-agent:token:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 注入 Redis 模板和 JSON 序列化器。
     */
    public RedisTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(AuthPrincipal principal) {
        try {
            String key = redisKey(principal.token());
            String value = objectMapper.writeValueAsString(principal);
            long ttlSeconds = Duration.between(principal.issuedAt(), principal.expiresAt()).toSeconds();
            if (ttlSeconds <= 0) {
                ttlSeconds = 86400; // 默认 1 天
            }
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException exception) {
            LOGGER.error("Failed to serialize AuthPrincipal to JSON", exception);
            throw new RuntimeException("Token serialization failed", exception);
        }
    }

    @Override
    public Optional<AuthPrincipal> findByToken(String token) {
        try {
            String key = redisKey(token);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            AuthPrincipal principal = objectMapper.readValue(value, AuthPrincipal.class);
            return Optional.of(principal);
        } catch (JsonProcessingException exception) {
            LOGGER.error("Failed to deserialize AuthPrincipal from JSON", exception);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(redisKey(token));
    }

    @Override
    public boolean isValid(String token) {
        String key = redisKey(token);
        Long ttl = redisTemplate.getExpire(key);
        return ttl != null && ttl > 0;
    }

    /**
     * 构造 Redis key 格式。
     */
    private static String redisKey(String token) {
        return KEY_PREFIX + token;
    }
}
