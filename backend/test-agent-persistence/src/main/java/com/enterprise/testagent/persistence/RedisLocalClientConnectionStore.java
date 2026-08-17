package com.enterprise.testagent.persistence;

import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis 精确连接路由；Lua 脚本保证旧 generation 无法续期、覆盖或删除新连接。 */
public class RedisLocalClientConnectionStore implements LocalClientConnectionStore {

    private static final String ROUTE_PREFIX = "test-agent:local-client:route:";
    private static final String GENERATION_KEY = "test-agent:local-client:generation";
    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'generation')
            if current and tonumber(current) >= tonumber(ARGV[1]) then
              return 0
            end
            redis.call('HSET', KEYS[1], 'generation', ARGV[1], 'payload', ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REFRESH_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'generation')
            if not current or current ~= ARGV[1] then
              return 0
            end
            redis.call('HSET', KEYS[1], 'payload', ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'generation')
            if not current or current ~= ARGV[1] then
              return 0
            end
            return redis.call('DEL', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisLocalClientConnectionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public long nextGeneration() {
        Long value = redis.opsForValue().increment(GENERATION_KEY);
        if (value == null || value < 1) {
            throw new IllegalStateException("local client connection generation allocation failed");
        }
        return value;
    }

    @Override
    public void save(LocalClientConnectionRoute route, Duration ttl) {
        requireTtl(ttl);
        Long saved = redis.execute(
                SAVE_SCRIPT,
                List.of(routeKey(route.clientInstanceId())),
                Long.toString(route.connectionGeneration()),
                write(route),
                Long.toString(ttl.toMillis()));
        if (!Long.valueOf(1L).equals(saved)) {
            throw new IllegalStateException("new local client route was fenced by a newer generation");
        }
    }

    @Override
    public Optional<LocalClientConnectionRoute> find(LocalClientInstanceId clientInstanceId) {
        Object value = redis.opsForHash().get(routeKey(clientInstanceId), "payload");
        return value == null ? Optional.empty() : Optional.of(read(value.toString()));
    }

    @Override
    public boolean refresh(LocalClientConnectionRoute route, Duration ttl) {
        requireTtl(ttl);
        Long refreshed = redis.execute(
                REFRESH_SCRIPT,
                List.of(routeKey(route.clientInstanceId())),
                Long.toString(route.connectionGeneration()),
                write(route),
                Long.toString(ttl.toMillis()));
        return Long.valueOf(1L).equals(refreshed);
    }

    @Override
    public boolean delete(LocalClientInstanceId clientInstanceId, long connectionGeneration) {
        Long deleted = redis.execute(
                DELETE_SCRIPT,
                List.of(routeKey(clientInstanceId)),
                Long.toString(connectionGeneration));
        return Long.valueOf(1L).equals(deleted);
    }

    private String write(LocalClientConnectionRoute route) {
        try {
            return objectMapper.writeValueAsString(route);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("local client route serialization failed", exception);
        }
    }

    private LocalClientConnectionRoute read(String json) {
        try {
            return objectMapper.readValue(json, LocalClientConnectionRoute.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("local client route deserialization failed", exception);
        }
    }

    private static String routeKey(LocalClientInstanceId clientInstanceId) {
        return ROUTE_PREFIX + clientInstanceId.value();
    }

    private static void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }
}
