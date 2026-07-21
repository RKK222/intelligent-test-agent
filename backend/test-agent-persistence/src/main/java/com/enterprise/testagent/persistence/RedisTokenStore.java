package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenStore;
import com.enterprise.testagent.domain.user.UserId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
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

    /**
     * 使用增量 SCAN 查找目标用户的历史 Token，避免管理员批量删除时执行阻塞式 KEYS。
     *
     * <p>Token 最长只保留一天，删除属于低频高权限操作；逐条解析现有 AuthPrincipal
     * 能兼容上线前已经签发、尚未建立用户反向索引的 Token。
     */
    @Override
    public void deleteByUserIds(Collection<UserId> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<String> targetUserIds = new HashSet<>();
        for (UserId userId : userIds) {
            if (userId != null) {
                targetUserIds.add(userId.value());
            }
        }
        if (targetUserIds.isEmpty()) {
            return;
        }

        List<String> matchedTokenKeys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(256).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String value = redisTemplate.opsForValue().get(key);
                if (value == null) {
                    continue;
                }
                try {
                    AuthPrincipal principal = objectMapper.readValue(value, AuthPrincipal.class);
                    if (targetUserIds.contains(principal.userId().value())) {
                        matchedTokenKeys.add(key);
                    }
                } catch (JsonProcessingException exception) {
                    // 兼容历史损坏值：不输出包含 token 的 Redis key，只记录一次低级别诊断。
                    LOGGER.debug("Ignored malformed auth principal while revoking deleted users");
                }
            }
        }
        if (!matchedTokenKeys.isEmpty()) {
            redisTemplate.delete(matchedTokenKeys);
        }
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
